package org.ethereum.beacon.pending;

import java.util.List;
import org.ethereum.beacon.core.operations.Attestation;

/** A pending state interface. */
public interface PendingState {

  List<Attestation> getAttestations();
}
