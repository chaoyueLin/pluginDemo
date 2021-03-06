/*
 * Copyright (c) 2016, Liu Dong
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.example.plugindemo.parser.utils.xml;

/**
 * Class holding various entity data for HTML and XML - generally for use with
 * the LookupTranslator.
 * All arrays are of length [*][2].
 */
public class EntityArrays {
    private static final String[][] BASIC_ESCAPE = {
            {"\"", "&quot;"}, // " - double-quote
            {"&", "&amp;"},   // & - ampersand
            {"<", "&lt;"},    // < - less-than
            {">", "&gt;"},    // > - greater-than
    };
    private static final String[][] APOS_ESCAPE = {
            {"'", "&apos;"}, // XML apostrophe
    };

    /**
     * Mapping to escape the basic XML and HTML character entities.
     * <p>
     * Namely: {@code " & < >}
     *
     * @return the mapping table
     */
    public static String[][] BASIC_ESCAPE() {
        return BASIC_ESCAPE.clone();
    }

    /**
     * Mapping to escape the apostrophe character to its XML character entity.
     *
     * @return the mapping table
     */
    public static String[][] APOS_ESCAPE() {
        return APOS_ESCAPE.clone();
    }

}
