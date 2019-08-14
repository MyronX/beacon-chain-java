package org.ethereum.beacon.db.rocksdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.ethereum.beacon.db.util.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class RocksDbSourceTest {

  @After
  @Before
  public void cleanUp() throws IOException {
    FileUtil.removeRecursively("test-db");
  }

  @Test
  public void basicOperations() {
    RocksDbSource rocksDb = new RocksDbSource(Paths.get("test-db"));

    rocksDb.open();
    rocksDb.put(wrap("ONE"), wrap("FIRST"));

    assertFalse(rocksDb.get(wrap("TWO")).isPresent());
    assertEquals(wrap("FIRST"), rocksDb.get(wrap("ONE")).get());

    Map<BytesValue, BytesValue> batch = new HashMap<>();
    batch.put(wrap("ONE"), null);
    batch.put(wrap("TWO"), wrap("SECOND"));
    batch.put(wrap("THREE"), wrap("THIRD"));
    batch.put(wrap("FOUR"), wrap("FOURTH"));

    rocksDb.batchUpdate(batch);

    assertFalse(rocksDb.get(wrap("ONE")).isPresent());
    assertEquals(wrap("SECOND"), rocksDb.get(wrap("TWO")).get());
    assertEquals(wrap("THIRD"), rocksDb.get(wrap("THREE")).get());
    assertEquals(wrap("FOURTH"), rocksDb.get(wrap("FOUR")).get());

    rocksDb.remove(wrap("THREE"));
    assertFalse(rocksDb.get(wrap("THREE")).isPresent());

    rocksDb.close();
    rocksDb.open();

    assertFalse(rocksDb.get(wrap("ONE")).isPresent());
    assertEquals(wrap("SECOND"), rocksDb.get(wrap("TWO")).get());
    assertFalse(rocksDb.get(wrap("THREE")).isPresent());
    assertEquals(wrap("FOURTH"), rocksDb.get(wrap("FOUR")).get());
    assertFalse(rocksDb.get(wrap("FIVE")).isPresent());

    rocksDb.close();
  }

  private BytesValue wrap(String value) {
    return BytesValue.wrap(value.getBytes());
  }
}
