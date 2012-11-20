/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.util.content;

import java.util.HashMap;

/**
 * Dictionary and content stream operands.
 */
public class OperandNames {

    public static int[] getType(byte ch[], int offset, int length) {
        byte c1, c2;
        byte c = ch[offset];
        /**
         * quickly switch though possible operands to find matching operands
         * as quickly as possible.  A few assumptions:
         * - tokens should be separated by spaces so the length should
         *   match the assumptions of the look ahead.
         * - if the length doesn't match then we likely have malformed
         *   stream and we tweak the offset so the next token can be found
         */
        switch (c) {
            case 'q':
                if (length == 1) return new int[]{OP_q, 0};
                else { // correct offset for missing white space;
                    return new int[]{OP_q, length - 1};
                }
            case 'Q':
                if (length == 1) return new int[]{OP_Q, 0};
                else { // correct offset for missing white space;
                    return new int[]{OP_Q, length - 1};
                }
            case 'r':
                c1 = ch[offset + 1];
                offset = 0;
                if (length > 2) {
                    offset = length - 2;
                }
                switch (c1) {
                    case 'e':
                        return new int[]{OP_re, offset};
                    case 'i':
                        return new int[]{OP_ri, offset};
                    default:
                        return new int[]{OP_rg, offset};
                }
            case 'R':
                offset = 0;
                if (length > 2) {
                    offset = length - 2;
                }
                return new int[]{OP_RG, offset};
            case 's':
                if (length == 1) {
                    return new int[]{OP_s, 0};
                }
                c1 = ch[offset + 1];
                switch (c1) {
                    case 'c':
                        if (length == 3) {
                            return new int[]{OP_scn, 0};
                        } else if (length == 2) {
                            return new int[]{OP_sc, 0};
                        } else if (length > 3) {
                            // need correction for off set without whitespace
                            c2 = ch[offset + 3];
                            if (c2 == 'n') {
                                // correct
                                offset = length - 3;
                                return new int[]{OP_scn, offset};
                            } else {
                                offset = length - 2;
                                return new int[]{OP_sc, offset};
                            }
                        }
                    case 'h':
                        if (length == 2) {
                            return new int[]{OP_sh, 0};
                        } else {
                            offset = length - 2;
                            return new int[]{OP_sh, offset};
                        }
                }
            case 'S':
                if (length == 1) {
                    return new int[]{OP_S, 0};
                }
                c1 = ch[offset + 1];
                switch (c1) {
                    case 'C':
                        if (length == 3) {
                            return new int[]{OP_SCN, 0};
                        } else if (length == 2) {
                            return new int[]{OP_SC, 0};
                        } else if (length > 3) {
                            // need correction for off set without whitespace
                            c2 = ch[offset + 3];
                            if (c2 == 'N') {
                                // correct
                                offset = length - 3;
                                return new int[]{OP_SCN, offset};
                            } else {
                                offset = length - 2;
                                return new int[]{OP_SC, offset};
                            }
                        }
                        break;
                }
                break;
            case 'T':
                c1 = ch[offset + 1];
                // all T operands are of length two.
                offset = 0;
                if (length > 2) {
                    offset = length - 2;
                }
                switch (c1) {
                    case 'c':
                        return new int[]{OP_Tc, offset};
                    case 'd':
                        return new int[]{OP_Td, offset};
                    case 'D':
                        return new int[]{OP_TD, offset};
                    case 'f':
                        return new int[]{OP_Tf, offset};
                    case 'j':
                        return new int[]{OP_Tj, offset};
                    case 'J':
                        return new int[]{OP_TJ, offset};
                    case 'L':
                        return new int[]{OP_TL, offset};
                    case 'm':
                        return new int[]{OP_Tm, offset};
                    case 'r':
                        return new int[]{OP_Tr, offset};
                    case 's':
                        return new int[]{OP_Ts, offset};
                    case 'w':
                        return new int[]{OP_Tw, offset};
                    case 'z':
                        return new int[]{OP_Tz, offset};
                    case '*':
                        return new int[]{OP_T_STAR, offset};
                }
            case 'f':
                if (length == 1) {
                    return new int[]{OP_f, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    if (c1 == '*') {
                        if (length > 2) {
                            offset = length - 2;
                        }
                        return new int[]{OP_f_STAR, offset};
                    } else {
                        offset = length - 1;
                        return new int[]{OP_f, offset};
                    }
                }
            case 'F':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_F, offset};
            case 'v':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_v, offset};
            case 'W':
                if (length == 1) {
                    return new int[]{OP_W, 0};
                } else {
                    c1 = ch[offset + 1];
                    if (c1 == '*') {
                        if (length == 2) {
                            return new int[]{OP_W_STAR, 0};
                        } else {
                            offset = length - 2;
                            return new int[]{OP_W_STAR, offset};
                        }
                    } else {
                        offset = length - 1;
                        return new int[]{OP_W, offset};
                    }
                }
            case 'w':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_w, offset};
            case 'n':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_n, offset};
            case 'y':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_y, offset};
            case 'E':
                if (length == 3) {
                    return new int[]{OP_EMC, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    switch (c1) {
                        case 'T':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_ET, offset};
                        case 'X':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_EX, offset};
                        case 'I':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_EI, offset};
                        case 'M':
                            if (length > 3) {
                                offset = length - 3;
                            }
                            return new int[]{OP_EMC, offset};
                    }
                }
            case 'i':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_i, offset};
            case 'h':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_h, offset};
            case 'j':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_j, offset};
            case 'J':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_J, offset};
            case 'k':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_k, offset};
            case 'K':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_K, offset};
            case 'G':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_G, offset};
            case 'l':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_l, offset};
            case 'L':
                offset = 0;
                if (length > 2) {
                    offset = length - 2;
                }
                return new int[]{OP_LW, offset};
            case 'g':
                if (length == 1) {
                    return new int[]{OP_g, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    if (c1 == 's') {
                        if (length > 2) {
                            offset = length - 2;
                        }
                        return new int[]{OP_gs, offset};
                    } else {
                        offset = length - 1;
                        return new int[]{OP_g, offset};
                    }
                }
            case 'C':
                offset = 0;
                if (length > 2) {
                    offset = length - 2;
                }
                return new int[]{OP_CS, offset};
            case 'c':
                if (length == 1) {
                    return new int[]{OP_c, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    switch (c1) {
                        case 's':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_cs, offset};
                        case 'm':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_cm, offset};
                        default:
                            offset = length - 1;
                            return new int[]{OP_c, offset};
                    }
                }
            case 'b':
                if (length == 1) {
                    return new int[]{OP_b, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    if (c1 == '*') {
                        if (length > 2) {
                            offset = length - 2;
                        }
                        return new int[]{OP_b_STAR, offset};
                    } else {
                        offset = length - 1;
                        return new int[]{OP_b, offset};
                    }
                }
            case 'B':
                if (length == 1) {
                    return new int[]{OP_B, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    switch (c1) {
                        case 'T':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_BT, offset};
                        case '*':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_B_STAR, offset};
                        case 'I':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_BI, offset};
                        case 'X':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_BX, offset};
                        case 'D':
                            if (length > 3) {
                                offset = length - 3;
                            }
                            return new int[]{OP_BDC, offset};
                        case 'M':
                            if (length > 3) {
                                offset = length - 3;
                            }
                            return new int[]{OP_BMC, offset};
                        default:
                            offset = length - 1;
                            return new int[]{OP_B, offset};
                    }
                }
            case 'd':
            case 'D':
                if (length == 1) {
                    return new int[]{OP_d, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    switch (c1) {
                        case '0':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_d0, offset};
                        case '1':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_d1, offset};
                        case 'o':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_Do, offset};
                        case 'P':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_DP, offset};
                        default:
                            offset = length - 1;
                            return new int[]{OP_d, offset};
                    }
                }
            case 'm':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_m, offset};
            case 'M':
                if (length == 1) {
                    return new int[]{OP_M, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    switch (c1) {
                        case 'P':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{OP_MP, offset};
                        default:
                            offset = length - 1;
                            return new int[]{OP_M, offset};
                    }
                }
            case 'I':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_ID, offset};
            case '\'':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_SINGLE_QUOTE, offset};
            case '"':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_DOUBLE_QUOTE, offset};
            case '%':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{OP_PERCENT, offset};
        }
        return new int[]{NO_OP, 0};
    }

    /**
     * Postscript subset of operations used in a PDF content streams
     */

    public static final int
            NO_OP = 0,
    // Path Tokens
    OP_m = 1,
            OP_l = 2,
            OP_c = 3,
            OP_v = 4,
            OP_y = 5,
            OP_h = 6,
            OP_re = 7,
    // Path Painting
    OP_S = 8,
            OP_s = 9,
            OP_f = 10,
            OP_F = 11,
            OP_f_STAR = 12,
            OP_B = 13,
            OP_b = 14,
            OP_B_STAR = 15,
            OP_b_STAR = 16,

    // external object
    OP_Do = 17,

    // clipping
    OP_n = 18,
            OP_W = 19,
            OP_W_STAR = 20,

    // compatibility
    OP_BX = 21,
            OP_EX = 22,

    // Marked Content
    OP_BDC = 23,
            OP_BMC = 24,
            OP_EMC = 25,

    // marked content
    OP_DP = 26,
            OP_MP = 27,

    // General Path Stats
    OP_ri = 28,
            OP_sh = 29,
            OP_d0 = 30,
            OP_d1 = 31,

    // text tokens
    OP_BT = 32,
            OP_ET = 33,
            OP_Tm = 34,
            OP_Td = 35,
            OP_TD = 36,
            OP_T_STAR = 37,
            OP_Tj = 38,
            OP_Tc = 39,
            OP_Tz = 40,
            OP_Tw = 41,
            OP_Tr = 42,
            OP_TL = 43,
            OP_Ts = 44,
            OP_TJ = 45,
            OP_Tf = 46,
            OP_SINGLE_QUOTE = 47,
            OP_DOUBLE_QUOTE = 48,

    // Color State
    OP_G = 49,
            OP_g = 50,
            OP_RG = 51,
            OP_rg = 52,
            OP_K = 53,
            OP_k = 54,
            OP_CS = 55,
            OP_cs = 56,
            OP_SC = 57,
            OP_SCN = 58,
            OP_sc = 59,
            OP_scn = 60,

    // Graphics STate
    OP_q = 61,
            OP_Q = 62,
            OP_cm = 63,
            OP_i = 64,
            OP_J = 65,
            OP_j = 66,
            OP_d = 67,
            OP_w = 68,
            OP_LW = 69,
            OP_M = 70,
            OP_gs = 71,

    // Inline Images
    OP_BI = 72,
            OP_EI = 73,
            OP_ID = 74,

    // comment
    OP_PERCENT = 75;

    public static HashMap<Integer, String> OPP_LOOKUP = new HashMap<Integer, String>();

    static {
        OPP_LOOKUP.put(0, "NO_OP");
        OPP_LOOKUP.put(1, "OP_m");
        OPP_LOOKUP.put(2, "OP_l");
        OPP_LOOKUP.put(3, "OP_c");
        OPP_LOOKUP.put(4, "OP_v");
        OPP_LOOKUP.put(5, "OP_y");
        OPP_LOOKUP.put(6, "OP_h");
        OPP_LOOKUP.put(7, "OP_re");
        OPP_LOOKUP.put(8, "OP_S");
        OPP_LOOKUP.put(9, "OP_s");
        OPP_LOOKUP.put(10, "OP_f");
        OPP_LOOKUP.put(11, "OP_F");
        OPP_LOOKUP.put(12, "OP_f_STAR");
        OPP_LOOKUP.put(13, "OP_B");
        OPP_LOOKUP.put(14, "OP_b");
        OPP_LOOKUP.put(15, "OP_B_STAR");
        OPP_LOOKUP.put(16, "OP_b_STAR");
        OPP_LOOKUP.put(17, "OP_Do");
        OPP_LOOKUP.put(18, "OP_n");
        OPP_LOOKUP.put(19, "OP_W");
        OPP_LOOKUP.put(20, "OP_W_STAR");
        OPP_LOOKUP.put(21, "OP_BX");
        OPP_LOOKUP.put(22, "OP_EX");
        OPP_LOOKUP.put(23, "OP_BDC");
        OPP_LOOKUP.put(24, "OP_BMC");
        OPP_LOOKUP.put(25, "OP_EMC");
        OPP_LOOKUP.put(26, "OP_DP");
        OPP_LOOKUP.put(27, "OP_MP");
        OPP_LOOKUP.put(28, "OP_ri");
        OPP_LOOKUP.put(29, "OP_sh");
        OPP_LOOKUP.put(30, "OP_d0");
        OPP_LOOKUP.put(31, "OP_d1");
        OPP_LOOKUP.put(32, "OP_BT");
        OPP_LOOKUP.put(33, "OP_ET");
        OPP_LOOKUP.put(34, "OP_Tm");
        OPP_LOOKUP.put(35, "OP_Td");
        OPP_LOOKUP.put(36, "OP_TD");
        OPP_LOOKUP.put(37, "OP_T_STAR");
        OPP_LOOKUP.put(38, "OP_Tj");
        OPP_LOOKUP.put(39, "OP_Tc");
        OPP_LOOKUP.put(40, "OP_Tz");
        OPP_LOOKUP.put(41, "OP_Tw");
        OPP_LOOKUP.put(42, "OP_Tr");
        OPP_LOOKUP.put(43, "OP_TL");
        OPP_LOOKUP.put(44, "OP_Ts");
        OPP_LOOKUP.put(45, "OP_TJ");
        OPP_LOOKUP.put(46, "OP_Tf");
        OPP_LOOKUP.put(47, "OP_SINGLE_QUOTE");
        OPP_LOOKUP.put(48, "OP_DOUBLE_QUOTE");
        OPP_LOOKUP.put(49, "OP_G");
        OPP_LOOKUP.put(50, "OP_g");
        OPP_LOOKUP.put(51, "OP_RG");
        OPP_LOOKUP.put(52, "OP_rg");
        OPP_LOOKUP.put(53, "OP_K");
        OPP_LOOKUP.put(54, "OP_k");
        OPP_LOOKUP.put(55, "OP_CS");
        OPP_LOOKUP.put(56, "OP_cs");
        OPP_LOOKUP.put(57, "OP_SC");
        OPP_LOOKUP.put(58, "OP_SCN");
        OPP_LOOKUP.put(59, "OP_sc");
        OPP_LOOKUP.put(60, "OP_scn");
        OPP_LOOKUP.put(61, "OP_q");
        OPP_LOOKUP.put(62, "OP_Q");
        OPP_LOOKUP.put(63, "OP_cm");
        OPP_LOOKUP.put(64, "OP_i");
        OPP_LOOKUP.put(65, "OP_J");
        OPP_LOOKUP.put(66, "OP_j");
        OPP_LOOKUP.put(67, "OP_d");
        OPP_LOOKUP.put(68, "OP_w");
        OPP_LOOKUP.put(69, "OP_LW");
        OPP_LOOKUP.put(70, "OP_M");
        OPP_LOOKUP.put(71, "OP_gs");
        OPP_LOOKUP.put(72, "OP_BI");
        OPP_LOOKUP.put(73, "OP_EI");
        OPP_LOOKUP.put(74, "OP_ID");

    }

}
