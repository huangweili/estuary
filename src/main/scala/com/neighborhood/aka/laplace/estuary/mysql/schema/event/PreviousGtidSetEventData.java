/*
 * Copyright 2017 Juan Olivares
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
package com.neighborhood.aka.laplace.estuary.mysql.schema.event;

/**
 * @author <a href="https://github.com/jolivares">Juan Olivares</a>
 */
public class PreviousGtidSetEventData implements EventData {

    private final String gtidSet;

    public PreviousGtidSetEventData(String gtidSet) {
        this.gtidSet = gtidSet;
    }

    public String getGtidSet() {
        return gtidSet;
    }

    @Override
    public String toString() {
        return "PreviousGtidSetEventData {gtidSet='" + gtidSet + "'}";
    }

}
