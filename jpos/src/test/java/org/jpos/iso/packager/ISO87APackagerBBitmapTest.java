/*
 * jPOS Project [http://jpos.org]
 * Copyright (C) 2000-2023 jPOS Software SRL
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jpos.iso.packager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class ISO87APackagerBBitmapTest {

    @Test
    public void testConstructor() throws Throwable {
        ISO87APackagerBBitmap iSO87APackagerBBitmap = new ISO87APackagerBBitmap();
        assertEquals(129, iSO87APackagerBBitmap.fld.length, "iSO87APackagerBBitmap.fld.length");
        assertNull(iSO87APackagerBBitmap.getLogger(), "iSO87APackagerBBitmap.getLogger()");
        assertNull(iSO87APackagerBBitmap.getRealm(), "iSO87APackagerBBitmap.getRealm()");
    }
}
