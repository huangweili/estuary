/*
 * Copyright 2013 Stanley Shyiko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.neighborhood.aka.laplace.estuary.mysql.schema.event.deserialization;

import com.neighborhood.aka.laplace.estuary.mysql.schema.event.FormatDescriptionEventData;
import com.neighborhood.aka.laplace.estuary.mysql.schema.io.ByteArrayInputStream;

import java.io.IOException;

/**
 * @author <a href="mailto:stanley.shyiko@gmail.com">Stanley Shyiko</a>
 */
public class FormatDescriptionEventDataDeserializer implements EventDataDeserializer<FormatDescriptionEventData> {

    @Override
    public FormatDescriptionEventData deserialize(ByteArrayInputStream inputStream) throws IOException {
        FormatDescriptionEventData eventData = new FormatDescriptionEventData();
        eventData.setBinlogVersion(inputStream.readInteger(2));
        eventData.setServerVersion(inputStream.readString(50).trim());
        inputStream.skip(4); // redundant, present in a header
        eventData.setHeaderLength(inputStream.readInteger(1));
        // lengths for all event types
        return eventData;
    }
}
