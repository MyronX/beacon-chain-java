/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.DelegatingBytesValue;

import static com.google.common.base.Preconditions.checkArgument;

public class LogTopic extends DelegatingBytesValue {

  public static final int SIZE = 32;

  private LogTopic(final BytesValue bytes) {
    super(bytes);
    checkArgument(
        bytes.size() == SIZE, "A log topic must be be %s bytes long, got %s", SIZE, bytes.size());
  }

  public static LogTopic create(final BytesValue bytes) {
    return new LogTopic(bytes);
  }

  public static LogTopic wrap(final BytesValue bytes) {
    return new LogTopic(bytes);
  }

  public static LogTopic of(final BytesValue bytes) {
    return new LogTopic(bytes.copy());
  }

  public static LogTopic fromHexString(final String str) {
    return new LogTopic(BytesValue.fromHexString(str));
  }
}
