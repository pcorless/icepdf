package org.icepdf.core.util.parser.content;

/**
 * PDF content stream operands.
 */
public class Operands {

    public static final int
            OP = 1,
            m = 2,
            l = 3,
            c = 4,
            v = 5,
            y = 6,
            h = 7,
            re = 8,
            S = 9,
            s = 10,
            f = 11,
            F = 12,
            f_STAR = 13,
            B = 14,
            b = 15,
            B_STAR = 16,
            b_STAR = 17,
            Do = 18,
            n = 19,
            W = 20,
            W_STAR = 21,
            BX = 22,
            EX = 23,
            BDC = 24,
            BMC = 25,
            EMC = 26,
            DP = 27,
            MP = 28,
            ri = 29,
            sh = 30,
            d0 = 31,
            d1 = 32,
            BT = 33,
            ET = 34,
            Tm = 35,
            Td = 36,
            TD = 37,
            T_STAR = 38,
            Tj = 39,
            Tc = 40,
            Tz = 41,
            Tw = 42,
            Tr = 43,
            TL = 44,
            Ts = 45,
            TJ = 46,
            Tf = 47,
            SINGLE_QUOTE = 48,
            DOUBLE_QUOTE = 49,
            G = 50,
            g = 51,
            RG = 52,
            rg = 53,
            K = 54,
            k = 55,
            CS = 56,
            cs = 57,
            SC = 58,
            SCN = 59,
            sc = 60,
            scn = 61,
            q = 62,
            Q = 63,
            cm = 64,
            i = 65,
            J = 66,
            j = 67,
            d = 68,
            w = 69,
            LW = 70,
            M = 71,
            gs = 72,
            BI = 73,
            EI = 74,
            ID = 75,
            PERCENT = 76,
            NULL = 77;

    public static int[] parseOperand(byte[] ch, int offset, int length) {
        byte c1, c2;
        byte c = ch[offset];
        switch (c) {
            case 'q':
                if (length == 1) return new int[]{q, 0};
                else {
                    return new int[]{q, length - 1};
                }
            case 'Q':
                if (length == 1) return new int[]{Q, 0};
                else {
                    return new int[]{Q, length - 1};
                }
            case 'r':
                c1 = ch[offset + 1];
                offset = 0;
                if (length > 2) {
                    offset = length - 2;
                }
                switch (c1) {
                    case 'e':
                        return new int[]{re, offset};
                    case 'i':
                        return new int[]{ri, offset};
                    default:
                        return new int[]{rg, offset};
                }
            case 'R':
                offset = 0;
                if (length > 2) {
                    offset = length - 2;
                }
                return new int[]{RG, offset};
            case 's':
                if (length == 1) {
                    return new int[]{s, 0};
                }
                c1 = ch[offset + 1];
                switch (c1) {
                    case 'c':
                        if (length == 3) {
                            return new int[]{scn, 0};
                        } else if (length == 2) {
                            return new int[]{sc, 0};
                        } else if (length > 3) {
                            c2 = ch[offset + 3];
                            if (c2 == 'n') {
                                offset = length - 3;
                                return new int[]{scn, offset};
                            } else {
                                offset = length - 2;
                                return new int[]{sc, offset};
                            }
                        }
                    case 'h':
                        if (length == 2) {
                            return new int[]{sh, 0};
                        } else {
                            offset = length - 2;
                            return new int[]{sh, offset};
                        }
                }
            case 'S':
                if (length == 1) {
                    return new int[]{S, 0};
                }
                c1 = ch[offset + 1];
                if (c1 == 'C') {
                    if (length == 3) {
                        return new int[]{SCN, 0};
                    } else if (length == 2) {
                        return new int[]{SC, 0};
                    } else if (length > 3) {
                        c2 = ch[offset + 3];
                        if (c2 == 'N') {
                            offset = length - 3;
                            return new int[]{SCN, offset};
                        } else {
                            offset = length - 2;
                            return new int[]{SC, offset};
                        }
                    }
                } else {
                    offset = length - 1;
                    return new int[]{S, offset};
                }
            case 'T':
                c1 = ch[offset + 1];
                // all T operands are of length two.
                offset = 0;
                if (length > 2) {
                    offset = length - 2;
                }
                switch (c1) {
                    case 'c':
                        return new int[]{Tc, offset};
                    case 'd':
                        return new int[]{Td, offset};
                    case 'D':
                        return new int[]{TD, offset};
                    case 'f':
                        return new int[]{Tf, offset};
                    case 'j':
                        return new int[]{Tj, offset};
                    case 'J':
                        return new int[]{TJ, offset};
                    case 'L':
                        return new int[]{TL, offset};
                    case 'm':
                        return new int[]{Tm, offset};
                    case 'r':
                        return new int[]{Tr, offset};
                    case 's':
                        return new int[]{Ts, offset};
                    case 'w':
                        return new int[]{Tw, offset};
                    case 'z':
                        return new int[]{Tz, offset};
                    case '*':
                        return new int[]{T_STAR, offset};
                }
            case 'f':
                if (length == 1) {
                    return new int[]{f, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    if (c1 == '*') {
                        if (length > 2) {
                            offset = length - 2;
                        }
                        return new int[]{f_STAR, offset};
                    } else {
                        offset = length - 1;
                        return new int[]{f, offset};
                    }
                }
            case 'F':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{F, offset};
            case 'v':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{v, offset};
            case 'W':
                if (length == 1) {
                    return new int[]{W, 0};
                } else {
                    c1 = ch[offset + 1];
                    if (c1 == '*') {
                        if (length == 2) {
                            return new int[]{W_STAR, 0};
                        } else {
                            offset = length - 2;
                            return new int[]{W_STAR, offset};
                        }
                    } else {
                        offset = length - 1;
                        return new int[]{W, offset};
                    }
                }
            case 'w':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{w, offset};
            case 'n':
                if (length == 1) {
                    return new int[]{n, 0};
                } else {
                    c1 = ch[offset + 1];
                    if (c1 == 'u') {
                        if (length > 3) {
                            offset = length - 3;
                        }
                        return new int[]{NULL, offset};
                    } else {
                        offset = length - 1;
                        return new int[]{n, offset};
                    }
                }
            case 'y':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{y, offset};
            case 'E':
                if (length == 3) {
                    return new int[]{EMC, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    switch (c1) {
                        case 'T':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{ET, offset};
                        case 'X':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{EX, offset};
                        case 'I':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{EI, offset};
                        case 'M':
                            if (length > 3) {
                                offset = length - 3;
                            }
                            return new int[]{EMC, offset};
                    }
                }
            case 'i':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{i, offset};
            case 'h':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{h, offset};
            case 'j':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{j, offset};
            case 'J':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{J, offset};
            case 'k':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{k, offset};
            case 'K':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{K, offset};
            case 'G':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{G, offset};
            case 'l':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{l, offset};
            case 'L':
                offset = 0;
                if (length > 2) {
                    offset = length - 2;
                }
                return new int[]{LW, offset};
            case 'g':
                if (length == 1) {
                    return new int[]{g, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    if (c1 == 's') {
                        if (length > 2) {
                            offset = length - 2;
                        }
                        return new int[]{gs, offset};
                    } else {
                        offset = length - 1;
                        return new int[]{g, offset};
                    }
                }
            case 'C':
                offset = 0;
                if (length > 2) {
                    offset = length - 2;
                }
                return new int[]{CS, offset};
            case 'c':
                if (length == 1) {
                    return new int[]{Operands.c, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    switch (c1) {
                        case 's':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{cs, offset};
                        case 'm':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{cm, offset};
                        default:
                            offset = length - 1;
                            return new int[]{Operands.c, offset};
                    }
                }
            case 'b':
                if (length == 1) {
                    return new int[]{b, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    if (c1 == '*') {
                        if (length > 2) {
                            offset = length - 2;
                        }
                        return new int[]{b_STAR, offset};
                    } else {
                        offset = length - 1;
                        return new int[]{b, offset};
                    }
                }
            case 'B':
                if (length == 1) {
                    return new int[]{B, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    switch (c1) {
                        case 'T':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{BT, offset};
                        case '*':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{B_STAR, offset};
                        case 'I':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{BI, offset};
                        case 'X':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{BX, offset};
                        case 'D':
                            if (length > 3) {
                                offset = length - 3;
                            }
                            return new int[]{BDC, offset};
                        case 'M':
                            if (length > 3) {
                                offset = length - 3;
                            }
                            return new int[]{BMC, offset};
                        default:
                            offset = length - 1;
                            return new int[]{B, offset};
                    }
                }
            case 'd':
            case 'D':
                if (length == 1) {
                    return new int[]{d, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    switch (c1) {
                        case '0':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{d0, offset};
                        case '1':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{d1, offset};
                        case 'o':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{Do, offset};
                        case 'P':
                            if (length > 2) {
                                offset = length - 2;
                            }
                            return new int[]{DP, offset};
                        default:
                            offset = length - 1;
                            return new int[]{d, offset};
                    }
                }
            case 'm':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{m, offset};
            case 'M':
                if (length == 1) {
                    return new int[]{M, 0};
                } else {
                    c1 = ch[offset + 1];
                    offset = 0;
                    if (c1 == 'P') {
                        if (length > 2) {
                            offset = length - 2;
                        }
                        return new int[]{MP, offset};
                    }
                    offset = length - 1;
                    return new int[]{M, offset};
                }
            case 'I':
                offset = 0;
                if (length > 2) {
                    offset = length - 2;
                }
                return new int[]{ID, offset};
            case '\'':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{SINGLE_QUOTE, offset};
            case '"':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{DOUBLE_QUOTE, offset};
            case '%':
                offset = 0;
                if (length > 1) {
                    offset = length - 1;
                }
                return new int[]{PERCENT, offset};
        }
        return new int[]{OP, 0};
    }
}
