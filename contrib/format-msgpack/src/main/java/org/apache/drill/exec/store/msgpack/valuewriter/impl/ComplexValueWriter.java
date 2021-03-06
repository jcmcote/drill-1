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
package org.apache.drill.exec.store.msgpack.valuewriter.impl;

import java.io.IOException;
import java.util.EnumMap;

import org.apache.drill.common.types.TypeProtos.DataMode;
import org.apache.drill.common.types.TypeProtos.MinorType;
import org.apache.drill.exec.record.MaterializedField;
import org.apache.drill.exec.record.metadata.ColumnMetadata;
import org.apache.drill.exec.store.msgpack.MsgpackParsingException;
import org.apache.drill.exec.store.msgpack.valuewriter.ExtensionValueWriter;
import org.apache.drill.exec.store.msgpack.valuewriter.ValueWriter;
import org.apache.drill.exec.vector.complex.fn.FieldSelection;
import org.apache.drill.exec.vector.complex.writer.BaseWriter.ListWriter;
import org.apache.drill.exec.vector.complex.writer.BaseWriter.MapWriter;
import org.msgpack.core.ExtensionTypeHeader;
import org.msgpack.core.MessageFormatException;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ValueType;
import org.slf4j.helpers.MessageFormatter;

/**
 * This is the base class for complex values either MAP or ARRAY. This class
 * central method is the writeElement method which handles writing any value
 * type MAP, ARRAY, BOOLEAN, STRING, FLOAT, INTEGER etc.
 */
public abstract class ComplexValueWriter extends AbstractValueWriter {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ComplexValueWriter.class);

  protected final EnumMap<ValueType, AbstractValueWriter> valueWriterMap;
  protected final ExtensionValueWriter[] extensionWriters;

  public ComplexValueWriter(EnumMap<ValueType, AbstractValueWriter> valueWriterMap,
      ExtensionValueWriter[] extensionWriters) {
    super();
    this.valueWriterMap = valueWriterMap;
    this.extensionWriters = extensionWriters;
  }

  protected MinorType getTypeSafe(MaterializedField schema) {
    if (schema != null) {
      return schema.getType().getMinorType();
    }
    return null;
  }

  protected DataMode getDataModeSafe(MaterializedField schema) {
    if (schema != null) {
      return schema.getDataMode();
    }
    return null;
  }

  /**
   * Writes a value into the given map or list.
   *
   * @param unpacker   the value to write
   * @param mapWriter
   * @param listWriter
   * @param fieldName
   * @param selection
   * @param schema     the desired schema for the given value
   * @throws IOException
   * @throws MessageFormatException
   */
  protected void writeElement(MessageUnpacker unpacker, MapWriter mapWriter, ListWriter listWriter, String fieldName,
      FieldSelection selection, ColumnMetadata schema) throws MessageFormatException, IOException {

    // ColumnMetadata cachedChildSchema = context.getFieldPathTracker().getSelectedColumnMetadata();
    // if (schema != null) {
    //   if (cachedChildSchema == null) {
    //     System.out.println("ERROR!!!!! COMPLEX no cached schema");
    //   } else if (!cachedChildSchema.isEquivalent(schema)) {
    //     System.out.println("ERROR!!!!! COMPLEX not equivalent");
    //   }
    // }

    // Get the type of the value. It can be any of the MAP, ARRAY, FLOAT, BOOLEAN,
    // STRING, INTEGER.
    ValueType valueType = unpacker.getNextFormat().getValueType();

    if (logger.isDebugEnabled()) {
      logDebug(valueType, mapWriter, fieldName, schema);
    }

    try {
      ValueWriter writer = null;

      if (valueType == ValueType.EXTENSION) {
        ExtensionTypeHeader header = unpacker.unpackExtensionTypeHeader();
        byte extType = header.getType();
        if (extType == -1) {
          extType = 0;
        }

        // Try to find extension type reader for given type.
        extensionWriters[extType].setExtensionTypeHeader(header);
        writer = extensionWriters[extType];
      } else {
        // We use that type to retrieve the corresponding writer.
        writer = valueWriterMap.get(valueType);
      }
      // Use writer to write the value into the drill map or list writers.
      writer.write(unpacker, mapWriter, fieldName, listWriter, selection, schema);
    } catch (Exception e) {
      String message = null;
      if (mapWriter != null) {
        message = MessageFormatter
            .arrayFormat("failed to write type: '{}' value: '{}' into map at '{}' target schema: '{}'\n",
                new Object[] { valueType, unpacker, context.getFieldPathTracker(), fieldName, schema })
            .getMessage();
      } else {
        message = MessageFormatter
            .arrayFormat("failed to write type: '{}' value: '{}' into list at '{}' target schema: '{}'\n",
                new Object[] { valueType, unpacker, context.getFieldPathTracker(), schema })
            .getMessage();
      }
      if (context.isLenient()) {
        context.warn(message, e);
      } else {
        throw new MsgpackParsingException(message, e);
      }
    }
  }

  private void logDebug(ValueType valueType, MapWriter mapWriter, String fieldName, ColumnMetadata schema) {
    if (schema != null) {
      logger.debug("write type: '{}' into {} at '{}' target type: '{}' mode: '{}'", valueType,
          mapWriter == null ? "list" : "map", context.getFieldPathTracker(), schema.type(), schema.mode());
    } else {
      logger.debug("write type: '{}' into {} at '{}'", valueType, mapWriter == null ? "list" : "map",
          context.getFieldPathTracker());
    }
  }
}
