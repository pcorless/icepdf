/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.ri.common;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * The KeyEvent Class contains the key event and input event used for menu
 * and button manipulatin via the keyboard.  This class may need to be changed
 * depending on region and languages.
 */
public class KeyEventConstants {

    // Get the correct menu shortcut key for the current platform
    public static final int MENU_SHORTCUT_KEY_MASK = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

    public static final int KEY_CODE_OPEN_FILE = KeyEvent.VK_O;
    public static final int MODIFIER_OPEN_FILE = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_OPEN_URL = KeyEvent.VK_U;
    public static final int MODIFIER_OPEN_URL = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_CLOSE = KeyEvent.VK_W;
    public static final int MODIFIER_CLOSE = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_SAVE_AS = KeyEvent.VK_S;
    public static final int MODIFIER_SAVE_AS = MENU_SHORTCUT_KEY_MASK | InputEvent.SHIFT_MASK;
    public static final int KEY_CODE_PRINT_SETUP = KeyEvent.VK_P;
    public static final int MODIFIER_PRINT_SETUP = MENU_SHORTCUT_KEY_MASK | InputEvent.SHIFT_MASK;
    public static final int KEY_CODE_PRINT = KeyEvent.VK_P;
    public static final int MODIFIER_PRINT = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_EXIT = KeyEvent.VK_Q;
    public static final int MODIFIER_EXIT = MENU_SHORTCUT_KEY_MASK;

    public static final int KEY_CODE_FIT_ACTUAL = KeyEvent.VK_1;
    public static final int MODIFIER_FIT_ACTUAL = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_FIT_PAGE = KeyEvent.VK_2;
    public static final int MODIFIER_FIT_PAGE = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_FIT_WIDTH = KeyEvent.VK_3;
    public static final int MODIFIER_FIT_WIDTH = MENU_SHORTCUT_KEY_MASK;

    public static final int KEY_CODE_ZOOM_IN = KeyEvent.VK_I;
    public static final int MODIFIER_ZOOM_IN = MENU_SHORTCUT_KEY_MASK | InputEvent.SHIFT_MASK;
    public static final int KEY_CODE_ZOOM_OUT = KeyEvent.VK_O;
    public static final int MODIFIER_ZOOM_OUT = MENU_SHORTCUT_KEY_MASK | InputEvent.SHIFT_MASK;

    public static final int KEY_CODE_ROTATE_LEFT = KeyEvent.VK_L;
    public static final int MODIFIER_ROTATE_LEFT = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_ROTATE_RIGHT = KeyEvent.VK_R;
    public static final int MODIFIER_ROTATE_RIGHT = MENU_SHORTCUT_KEY_MASK;

    public static final int KEY_CODE_FIRST_PAGE = KeyEvent.VK_UP;
    public static final int MODIFIER_FIRST_PAGE = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_PREVIOUS_PAGE = KeyEvent.VK_LEFT;
    public static final int MODIFIER_PREVIOUS_PAGE = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_NEXT_PAGE = KeyEvent.VK_RIGHT;
    public static final int MODIFIER_NEXT_PAGE = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_LAST_PAGE = KeyEvent.VK_DOWN;
    public static final int MODIFIER_LAST_PAGE = MENU_SHORTCUT_KEY_MASK;

    public static final int KEY_CODE_SEARCH = KeyEvent.VK_S;
    public static final int MODIFIER_SEARCH = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_GOTO = KeyEvent.VK_N;
    public static final int MODIFIER_GOTO = MENU_SHORTCUT_KEY_MASK;

    public static final int KEY_CODE_UNDO = KeyEvent.VK_Z;
    public static final int MODIFIER_UNDO = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_REDO = KeyEvent.VK_Z;
    public static final int MODIFIER_REDO = MENU_SHORTCUT_KEY_MASK | InputEvent.SHIFT_MASK;
    public static final int KEY_CODE_COPY = KeyEvent.VK_C;
    public static final int MODIFIER_COPY = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_DELETE = KeyEvent.VK_D;
    public static final int MODIFIER_DELETE = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_SELECT_ALL = KeyEvent.VK_A;
    public static final int MODIFIER_SELECT_ALL = MENU_SHORTCUT_KEY_MASK;
    public static final int KEY_CODE_DESELECT_ALL = KeyEvent.VK_A;
    public static final int MODIFIER_DESELECT_ALL = MENU_SHORTCUT_KEY_MASK | InputEvent.SHIFT_MASK;
}
