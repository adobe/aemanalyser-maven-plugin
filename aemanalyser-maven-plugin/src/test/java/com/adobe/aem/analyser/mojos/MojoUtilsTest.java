/*
  Copyright 2020 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.mojos;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MojoUtilsTest {
    @Test
    public void testSetParameter() throws Exception {
        TestClass tc = new TestClass();

        assertNull("Precondition", tc.myField);
        MojoUtils.setParameter(tc, "myField", "hello");
        assertEquals("hello", tc.myField);

        assertFalse("Precondition", ((TestSuperClass) tc).mySuperField);
        MojoUtils.setParameter(tc, "mySuperField", true);
        assertTrue(((TestSuperClass) tc).mySuperField);
    }

    private static class TestSuperClass {
        private boolean mySuperField;
    }

    private static class TestClass extends TestSuperClass {
        private String myField;
    }
}
