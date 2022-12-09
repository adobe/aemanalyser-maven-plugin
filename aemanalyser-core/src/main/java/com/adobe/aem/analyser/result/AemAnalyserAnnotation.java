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

import java.io.File;
import java.util.Objects;

public class AemAnalyserAnnotation {

    public enum Level {
        error,
        warning
    };

    private final File source;
        
    private final long lineNumber;

    private final long columnNumber;

    private final String message;

    public AemAnalyserAnnotation(final String message) {
        this(null, message);
    }

    public AemAnalyserAnnotation(final File source, final String message) {
        this(source, message, -1, -1);
    }

    public AemAnalyserAnnotation(final File source, final String message, final long lnr, final long cnr) {
        this.source = source;
        this.message = message;
        this.lineNumber = lnr;
        this.columnNumber = cnr;
    }

    /**
     * @return the source
     */
    public File getSource() {
        return source;
    }

    /**
     * @return the lineNumber
     */
    public long getLineNumber() {
        return lineNumber;
    }

    /**
     * @return the columnNumber
     */
    public long getColumnNumber() {
        return columnNumber;
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return this.message;
    }

    public String toString() {
        if ( this.getSource() == null ) {
            return this.getMessage();
        }
        String location = null;
        if ( this.getLineNumber() != -1 ) {
            if ( this.getColumnNumber() != -1 ) {
                location = "[".concat(String.valueOf(this.getLineNumber())).concat(":").concat(String.valueOf(this.getColumnNumber())).concat("]");
            } else {
                location = "[".concat(String.valueOf(this.getLineNumber())).concat("]");
            }
        }
        return this.getSource().getAbsolutePath().concat(location == null ? "" : location).concat(": ").concat(this.getMessage());
    }

    public String toString(final File rootDirectory) {
        if ( this.getSource() == null ) {
            return this.getMessage();
        }
        String location = "";
        if ( this.getLineNumber() != -1 ) {
            if ( this.getColumnNumber() != -1 ) {
                location = " [".concat(String.valueOf(this.getLineNumber())).concat(":").concat(String.valueOf(this.getColumnNumber())).concat("]");
            } else {
                location = " [".concat(String.valueOf(this.getLineNumber())).concat("]");
            }
        }
        final String file = this.getSource().getAbsolutePath().substring(rootDirectory.getAbsolutePath().length() + 1);
        return file.concat(location).concat(": ").concat(this.getMessage());
    }

    public String toMessage(final Level level, final File rootDirectory) {
        final String prefix = "::".concat(level.name()).concat(" ");
        final String postfix = "::".concat(this.getMessage());
        if ( this.getSource() == null ) {
            return prefix.concat(postfix);
        }
        final String location;
        if ( this.getLineNumber() != - 1) {
            if ( this.getColumnNumber() != -1 ) {
                location = ",line=".concat(String.valueOf(this.getLineNumber())).concat(",col=").concat(String.valueOf(this.getColumnNumber()));
            } else {
                location = ",line=".concat(String.valueOf(this.getLineNumber()));
            }
        } else {
            location = "";
        }
        final String file = this.getSource().getAbsolutePath().substring(rootDirectory.getAbsolutePath().length() + 1);
        return prefix.concat("file=").concat(file).concat(location).concat(postfix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, lineNumber, columnNumber, message);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof AemAnalyserAnnotation))
            return false;
            AemAnalyserAnnotation other = (AemAnalyserAnnotation) obj;
        return Objects.equals(source, other.source) && lineNumber == other.lineNumber
                && columnNumber == other.columnNumber && Objects.equals(message, other.message);
    }
}
