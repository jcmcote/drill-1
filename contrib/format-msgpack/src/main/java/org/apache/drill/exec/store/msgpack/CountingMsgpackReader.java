/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.exec.store.msgpack;

import java.io.IOException;

import org.apache.drill.exec.record.MaterializedField;
import org.apache.drill.exec.vector.complex.writer.BaseWriter.ComplexWriter;
import org.msgpack.value.Value;

public class CountingMsgpackReader extends BaseMsgpackReader {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CountingMsgpackReader.class);

  public CountingMsgpackReader() {
  }

  @Override
  protected ReadState writeRecord(Value mapValue, ComplexWriter writer, MaterializedField schema) throws IOException {
    writer.rootAsMap().bit("count").writeBit(1);
    return ReadState.WRITE_SUCCEED;
  }

}