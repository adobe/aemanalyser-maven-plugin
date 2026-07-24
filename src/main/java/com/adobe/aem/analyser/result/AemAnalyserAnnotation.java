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
package com.adobe.aem.analyser.result;

import java.util.Objects;

public class AemAnalyserAnnotation {

    public enum Level {
        error,
        warning
    };

    private final String message;

    public AemAnalyserAnnotation(final String message) {
        this.message = message;
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return this.message;
    }

    public String toString() {
        return this.getMessage();
    }

    @Override
    public int hashCode() {
        return Objects.hash(message);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof AemAnalyserAnnotation))
            return false;
        AemAnalyserAnnotation other = (AemAnalyserAnnotation) obj;
        return Objects.equals(message, other.message);
    }
}
