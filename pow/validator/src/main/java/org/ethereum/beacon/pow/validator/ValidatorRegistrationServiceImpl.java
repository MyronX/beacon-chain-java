package org.ethereum.beacon.pow.validator;

import java.util.Collections;
import org.ethereum.beacon.chain.observer.ObservableBeaconState;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.BlockTransition;
import org.ethereum.beacon.consensus.SpecHelpers;
import org.ethereum.beacon.consensus.StateTransition;
import org.ethereum.beacon.consensus.transition.PerBlockTransition;
import org.ethereum.beacon.consensus.transition.PerEpochTransition;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.deposit.DepositInput;
import org.ethereum.beacon.core.state.ValidatorRecord;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.db.source.SingleValueSource;
import org.ethereum.beacon.pow.DepositContract;
import org.ethereum.beacon.schedulers.Schedulers;
import org.ethereum.beacon.ssz.Serializer;
import org.ethereum.beacon.validator.BeaconChainAttester;
import org.ethereum.beacon.validator.BeaconChainProposer;
import org.ethereum.beacon.validator.MultiValidatorService;
import org.ethereum.beacon.validator.ValidatorService;
import org.ethereum.beacon.validator.attester.BeaconChainAttesterImpl;
import org.ethereum.beacon.validator.crypto.BLS381Credentials;
import org.ethereum.beacon.validator.proposer.BeaconChainProposerImpl;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import tech.pegasys.artemis.ethereum.core.Address;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes8;
import tech.pegasys.artemis.util.bytes.BytesValue;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Collections.singletonList;
import static org.ethereum.beacon.core.spec.SignatureDomains.DEPOSIT;

public class ValidatorRegistrationServiceImpl implements ValidatorRegistrationService {
  private final TransactionBuilder transactionBuilder;
  private final TransactionGateway transactionGateway;
  private final DepositContract depositContract;
  private final Publisher<ObservableBeaconState> observablePublisher;
  private final SingleValueSource<RegistrationStage> stagePersistence;
  private final Schedulers schedulers;

  private final SpecHelpers spec;
  private final Serializer sszSerializer;

  private Disposable depositSubscription = null;
  private ValidatorService validatorService = null;
  private RegistrationStage currentStage = null;
  private ValidatorRecord validatorRecord = null;

  // Validator
  private BLS381Credentials blsCredentials;
  private Hash32 withdrawalCredentials = null;
  private Gwei amount = null;
  private Address eth1From = null;
  private BytesValue eth1PrivKey = null;

  private ExecutorService executor;

  public ValidatorRegistrationServiceImpl(
      TransactionBuilder transactionBuilder,
      TransactionGateway transactionGateway,
      DepositContract depositContract,
      Publisher<ObservableBeaconState> observablePublisher,
      SingleValueSource<RegistrationStage> registrationStagePersistence,
      SpecHelpers spec,
      Schedulers schedulers) {
    this.transactionBuilder = transactionBuilder;
    this.transactionGateway = transactionGateway;
    this.depositContract = depositContract;
    this.observablePublisher = observablePublisher;
    this.stagePersistence = registrationStagePersistence;
    this.spec = spec;
    this.schedulers = schedulers;
    sszSerializer = Serializer.annotationSerializer();
  }

  @Override
  public void start(
      BLS381Credentials credentials,
      @Nullable Hash32 withdrawalCredentials,
      @Nullable Gwei amount,
      @Nullable Address eth1From,
      @Nullable BytesValue eth1PrivKey) {
    this.executor =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread t = new Thread(runnable, "validator-registration-service");
              t.setDaemon(true);
              return t;
            });
    actionCircle();
  }

  private void actionCircle() {
    RegistrationStage currentStage = getCurrentStage();
    getCurrentStageFunction(currentStage).run();
  }

  private void changeCurrentStage(RegistrationStage newStage) {
    this.currentStage = newStage;
    stagePersistence.set(currentStage);
    actionCircle();
  }

  private RegistrationStage getCurrentStage() {
    if (currentStage != null) {
      return currentStage;
    }

    return stagePersistence
        .get()
        .orElseGet(() -> findRegistrationStage().orElse(RegistrationStage.SEND_TX));
  }

  /**
   * Sometimes current registration stage could be discovered indirectly without stage persistence
   * data, for example, when our validator is already registered or active. In such cases this
   * method will return registration stage, otherwise Optional.empty()
   */
  private Optional<RegistrationStage> findRegistrationStage() {
    BeaconState latestState = getLatestState();
    Optional<ValidatorRecord> validatorRecordOptional =
        latestState.getValidatorRegistry().stream()
            .filter(record -> record.getPubKey().equals(blsCredentials.getPubkey()))
            .findFirst();
    if (!validatorRecordOptional.isPresent()) {
      return Optional.empty();
    }

    this.validatorRecord = validatorRecordOptional.get();
    EpochNumber currentEpoch = spec.get_current_epoch(latestState);
    if (spec.is_active_validator(validatorRecord, currentEpoch)) {
      return Optional.of(RegistrationStage.VALIDATOR_START);
    } else {
      return Optional.of(RegistrationStage.AWAIT_ACTIVATION);
    }
  }

  private Runnable getCurrentStageFunction(RegistrationStage stage) {
    switch (stage) {
      case SEND_TX:
        {
          return () -> executor.execute(this::sendDepositAndWait);
        }
      case AWAIT_INCLUSION:
        {
          return () -> executor.execute(this::awaitInclusion);
        }
      case AWAIT_ACTIVATION:
        {
          return () -> executor.execute(this::awaitActivation);
        }
      case VALIDATOR_START:
        {
          return () -> executor.execute(this::startValidator);
        }
      case COMPLETE:
        {
          return () -> executor.shutdown();
        }
      default:
        {
          throw new RuntimeException(
              String.format("Service doesn't know how to handle registration stage %s", stage));
        }
    }
  }

  private void awaitActivation() {
    Flux.from(observablePublisher)
        .subscribe(
            observableBeaconState -> {
              BeaconState latestState = observableBeaconState.getLatestSlotState();
              EpochNumber currentEpoch = spec.get_current_epoch(latestState);
              if (spec.is_active_validator(validatorRecord, currentEpoch)) {
                changeCurrentStage(RegistrationStage.VALIDATOR_START);
              }
            });
  }

  private void startValidator() {
    if (validatorService == null) {
      BlockTransition<BeaconStateEx> blockTransition = new PerBlockTransition(spec);
      StateTransition<BeaconStateEx> epochTransition = new PerEpochTransition(spec);
      BeaconChainProposer proposer =
          new BeaconChainProposerImpl(
              spec,
              blockTransition,
              epochTransition,
              depositContract);
      BeaconChainAttester attester =
          new BeaconChainAttesterImpl(spec);
      validatorService =
          new MultiValidatorService(
              singletonList(blsCredentials),
              proposer,
              attester,
              spec,
              observablePublisher,
              schedulers);
      validatorService.start();
      changeCurrentStage(RegistrationStage.COMPLETE);
    }
  }

  private BeaconState getLatestState() {
    BeaconState latestState = null;
    try {
      latestState = Flux.from(observablePublisher).next().toFuture().get().getLatestSlotState();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException("Cannot obtain latest state from Observable state publisher");
    }
    return latestState;
  }

  private void awaitInclusion() {
    if (depositSubscription == null) {
      depositSubscription =
          Flux.from(depositContract.getDepositStream())
              .subscribe(
                  deposit -> {
                    onDeposit(deposit)
                        .ifPresent(
                            d -> {
                              changeCurrentStage(RegistrationStage.AWAIT_ACTIVATION);
                              validatorRecord =
                                  getLatestState().getValidatorRegistry().stream()
                                      .filter(
                                          record -> {
                                            return record.getPubKey().equals(blsCredentials.getPubkey());
                                          })
                                      .findFirst()
                                      .get();
                              depositSubscription.dispose();
                              depositSubscription = null;
                            });
                  });
    }
  }

  private void sendDepositAndWait() {
    submitDeposit(amount, eth1From, eth1PrivKey)
        .thenRun(() -> changeCurrentStage(RegistrationStage.AWAIT_INCLUSION));
  }

  private Optional<Deposit> onDeposit(Deposit deposit) {
    return Optional.of(deposit)
        .filter(d -> d.getDepositData().getDepositInput().getPubKey().equals(blsCredentials.getPubkey()));
  }

  private CompletableFuture<TransactionGateway.TxStatus> submitDeposit(
      Gwei amount, Address eth1From, BytesValue eth1PrivKey) {
    DepositInput depositInput = createDepositInput();
    return createTransaction(eth1From, eth1PrivKey, depositInput, amount)
        .thenCompose(transactionGateway::send);
  }

  private DepositInput createDepositInput() {
    // To submit a deposit:
    //
    //    Pack the validator's initialization parameters into deposit_input, a DepositInput SSZ
    // object.
    //    Set deposit_input.proof_of_possession = EMPTY_SIGNATURE.
    DepositInput preDepositInput =
        new DepositInput(blsCredentials.getPubkey(), withdrawalCredentials, BLSSignature.ZERO);
    // Let proof_of_possession be the result of bls_sign of the hash_tree_root(deposit_input) with
    // domain=DOMAIN_DEPOSIT.
    Hash32 hash = spec.signed_root(preDepositInput, "proofOfPossession");
    BeaconState latestState = getLatestState();
    Bytes8 domain =
        spec.get_domain(
            latestState.getForkData(), spec.get_current_epoch(latestState), DEPOSIT);
    BLSSignature signature = blsCredentials.getSigner().sign(hash, domain);
    // Set deposit_input.proof_of_possession = proof_of_possession.
    DepositInput depositInput = new DepositInput(blsCredentials.getPubkey(), withdrawalCredentials, signature);

    return depositInput;
  }

  @Override
  public Optional<ValidatorService> getValidatorService() {
    return Optional.ofNullable(validatorService);
  }

  private CompletableFuture<BytesValue> createTransaction(
      Address eth1From, BytesValue eth1PrivKey, DepositInput depositInput, Gwei amount) {
    // Let amount be the amount in Gwei to be deposited by the validator where MIN_DEPOSIT_AMOUNT <=
    // amount <= MAX_DEPOSIT_AMOUNT.
    if (amount.compareTo(spec.getConstants().getMinDepositAmount()) < 0) {
      throw new RuntimeException(
          String.format(
              "Deposit amount should be equal or greater than %s (defined by spec)",
              spec.getConstants().getMinDepositAmount()));
    }
    if (amount.compareTo(spec.getConstants().getMaxDepositAmount()) > 0) {
      throw new RuntimeException(
          String.format(
              "Deposit amount should be equal or less than %s (defined by spec)",
              spec.getConstants().getMaxDepositAmount()));
    }
    // Send a transaction on the Ethereum 1.0 chain to DEPOSIT_CONTRACT_ADDRESS executing deposit
    // along with serialize(deposit_input) as the singular bytes input along with a deposit amount
    // in Gwei.
    return transactionBuilder
        .createTransaction(eth1From.toString(), sszSerializer.encode2(depositInput), amount)
        .thenCompose(unsignedTx -> transactionBuilder.signTransaction(unsignedTx, eth1PrivKey));
  }
}
