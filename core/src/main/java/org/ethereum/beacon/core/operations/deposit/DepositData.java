package org.ethereum.beacon.core.operations.deposit;

import com.google.common.base.Objects;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;

/**
 * A data of validator registration deposit.
 *
 * @see Deposit
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.6.1/specs/core/0_beacon-chain.md#depositdata">DepositData
 *     in the spec</a>
 */
@SSZSerializable
public class DepositData {

  /** BLS public key. */
  @SSZ private final BLSPubkey pubKey;
  /** Withdrawal credentials. */
  @SSZ private final Hash32 withdrawalCredentials;
  /** Amount in Gwei. */
  @SSZ private final Gwei amount;
  /** Container self-signature */
  @SSZ private final BLSSignature signature;

  public DepositData(
      BLSPubkey pubKey, Hash32 withdrawalCredentials, Gwei amount, BLSSignature signature) {
    this.pubKey = pubKey;
    this.withdrawalCredentials = withdrawalCredentials;
    this.amount = amount;
    this.signature = signature;
  }

  public Gwei getAmount() {
    return amount;
  }

  public BLSPubkey getPubKey() {
    return pubKey;
  }

  public Hash32 getWithdrawalCredentials() {
    return withdrawalCredentials;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DepositData that = (DepositData) o;
    return Objects.equal(pubKey, that.pubKey)
        && Objects.equal(withdrawalCredentials, that.withdrawalCredentials)
        && Objects.equal(amount, that.amount)
        && Objects.equal(signature, that.signature);
  }

  @Override
  public int hashCode() {
    int result = pubKey.hashCode();
    result = 31 * result + withdrawalCredentials.hashCode();
    result = 31 * result + amount.hashCode();
    result = 31 * result + signature.hashCode();
    return result;
  }
}
