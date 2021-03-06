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

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.apache.drill.exec.record.metadata.ColumnMetadata;
import org.apache.drill.exec.record.metadata.TupleMetadata;

public class FieldPathTracker {

  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FieldPathTracker.class);

  private Field current;

  private final Field rootField;

  public FieldPathTracker() {
    this.rootField = new Field();
    this.current = null;
  }

  public String select(ByteBuffer byteBuffer) throws UnsupportedEncodingException {
    return current.select(byteBuffer);
  }

  public void enterMap() {
    if (current == null) {
      current = rootField;
    } else {
      current = current.enterMap();
    }
  }

  public void enterArray() {
    current = current.enterArray();
  }

  public void leaveMap() {
    current = current.leaveMap();
  }

  public void leaveArray() {
    current = current.leaveArray();
  }

  @Override
  public String toString() {
    if (current == null) {
      return "root";
    }
    return rootField.toString();
  }

  public void setSchema(TupleMetadata tupleMetadata) {
    this.rootField.setTupleMetadata(tupleMetadata);
  }

  public ColumnMetadata getColumnMetadata() {
    if (current == null) {
      return rootField.getColumnMetadata();
    }
    if(this.current==null){
      System.out.println("FIELDTRACKER!!!!!!! getColumnMetadata null current");
      return null;
    }
      return this.current.getColumnMetadata();
  }

public ColumnMetadata getSelectedColumnMetadata() {
  if (current == null) {
    return rootField.getSelectedColumnMetadata();
  }
  if(this.current==null){
    System.out.println("FIELDTRACKER!!!!!!!! getSelectedColumnMetadata null current");
    return null;
  }
  return this.current.getSelectedColumnMetadata();
}
}