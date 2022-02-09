/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser;

/**
 * Represents a variation of the product to generate an aggregate for, such as author or publish
 *
 * <p>Implementations of this interface should implement the {@link Object#equals(Object)} and {@link Object#hashCode()}
 * methods.</p>
 *
 */
public interface ProductVariation {

    /**
     * Returns the name of the product aggregate to generate for this variation
     *
     * @return the name of the product aggregate
     */
    default String getProductAggregateName() {
        return "product-" + getFinalAggregateName();
    }

    /**
     * Returns the name of the final aggregate to generate for this variation
     *
     * @return the name of the final aggregate
     */
    String getFinalAggregateName();
}