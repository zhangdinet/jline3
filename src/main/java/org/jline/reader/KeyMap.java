/*
 * Copyright (c) 2002-2015, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package org.jline.reader;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.jline.utils.Log;

/**
 * The KeyMap class contains all bindings from keys to operations.
 *
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 * @since 2.6
 */
public class KeyMap {

    public static final String VI_MOVE = "vi-move";
    public static final String VI_INSERT = "vi-insert";
    public static final String EMACS = "emacs";
    public static final String EMACS_STANDARD = "emacs-standard";
    public static final String EMACS_CTLX = "emacs-ctlx";
    public static final String EMACS_META = "emacs-meta";
    public static final String MENU_SELECT = "menuselect";

    private static final int KEYMAP_LENGTH = 256;

    private static final Object NULL_FUNCTION = new Object();

    private Object[] mapping = new Object[KEYMAP_LENGTH];
    private Object anotherKey = null;
    private String name;

    public KeyMap(String name) {
        this(name, new Object[KEYMAP_LENGTH]);
    }

    protected KeyMap(String name, Object[] mapping) {
        this.mapping = mapping;
        this.name = name;
    }

    public static void bindKey(KeyMap keys, String seq, String val) {
        seq = translate(seq);
        if (val.length() > 0 && (val.charAt(0) == '\'' || val.charAt(0) == '\"')) {
            keys.bind(seq, new Macro(translateQuoted(val)));
        } else {
            String operationName = val.replace('-', '_').toUpperCase();
            try {
                keys.bind(seq, Operation.valueOf(operationName));
            } catch(IllegalArgumentException e) {
                Log.info("Unable to bind key for unsupported operation: ", val);
            }
        }
    }

    public static String translate(String seq) {
        if (seq.charAt(0) == '"') {
            seq = translateQuoted(seq);
        } else {
            // Bind key name
            String keyName = seq.lastIndexOf('-') > 0 ? seq.substring( seq.lastIndexOf('-') + 1 ) : seq;
            char key = getKeyFromName(keyName);
            keyName = seq.toLowerCase();
            seq = "";
            if (keyName.contains("meta-") || keyName.contains("m-")) {
                seq += "\u001b";
            }
            if (keyName.contains("control-") || keyName.contains("c-")
                    || keyName.contains("ctrl-")) {
                key = (char)(Character.toUpperCase( key ) & 0x1f);
            }
            seq += key;
        }
        return seq;
    }

    private static String translateQuoted(String keySeq) {
        int i;
        String str = keySeq.substring( 1, keySeq.length() - 1 );
        keySeq = "";
        for (i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\') {
                boolean ctrl = str.regionMatches(i, "\\C-", 0, 3)|| str.regionMatches(i, "\\M-\\C-", 0, 6);
                boolean meta = str.regionMatches(i, "\\M-", 0, 3)|| str.regionMatches(i, "\\C-\\M-", 0, 6);
                i += (meta ? 3 : 0) + (ctrl ? 3 : 0) + (!meta && !ctrl ? 1 : 0);
                if (i >= str.length()) {
                    break;
                }
                c = str.charAt(i);
                if (meta) {
                    keySeq += "\u001b";
                }
                if (ctrl) {
                    c = c == '?' ? 0x7f : (char)(Character.toUpperCase( c ) & 0x1f);
                }
                if (!meta && !ctrl) {
                    switch (c) {
                        case 'a': c = 0x07; break;
                        case 'b': c = '\b'; break;
                        case 'd': c = 0x7f; break;
                        case 'e': c = 0x1b; break;
                        case 'f': c = '\f'; break;
                        case 'n': c = '\n'; break;
                        case 'r': c = '\r'; break;
                        case 't': c = '\t'; break;
                        case 'v': c = 0x0b; break;
                        case '\\': c = '\\'; break;
                        case '0': case '1': case '2': case '3':
                        case '4': case '5': case '6': case '7':
                            c = 0;
                            for (int j = 0; j < 3; j++, i++) {
                                if (i >= str.length()) {
                                    break;
                                }
                                int k = Character.digit(str.charAt(i), 8);
                                if (k < 0) {
                                    break;
                                }
                                c = (char)(c * 8 + k);
                            }
                            c &= 0xFF;
                            break;
                        case 'x':
                            i++;
                            c = 0;
                            for (int j = 0; j < 2; j++, i++) {
                                if (i >= str.length()) {
                                    break;
                                }
                                int k = Character.digit(str.charAt(i), 16);
                                if (k < 0) {
                                    break;
                                }
                                c = (char)(c * 16 + k);
                            }
                            c &= 0xFF;
                            break;
                        case 'u':
                            i++;
                            c = 0;
                            for (int j = 0; j < 4; j++, i++) {
                                if (i >= str.length()) {
                                    break;
                                }
                                int k = Character.digit(str.charAt(i), 16);
                                if (k < 0) {
                                    break;
                                }
                                c = (char)(c * 16 + k);
                            }
                            break;
                    }
                }
                keySeq += c;
            } else {
                keySeq += c;
            }
        }
        return keySeq;
    }

    private static char getKeyFromName(String org) {
        String name = org.toLowerCase();
        switch (name) {
            case "del":
            case "rubout":
                return 0x7f;
            case "esc":
            case "escape":
                return '\033';
            case "lfd":
            case "newline":
                return '\n';
            case "ret":
            case "return":
                return '\r';
            case "spc":
            case "space":
                return ' ';
            case "tab":
                return '\t';
            default:
                return org.charAt(0);
        }
    }

    public String getName() {
        return name;
    }

    public Object getAnotherKey() {
        return anotherKey;
    }

    public void from(KeyMap other) {
        this.mapping = other.mapping;
        this.anotherKey = other.anotherKey;
    }

    public boolean isPrefix(CharSequence keySeq) {
        if (keySeq != null && keySeq.length() > 0) {
            KeyMap map = this;
            for (int i = 0; i < keySeq.length(); i++) {
                char c = keySeq.charAt(i);
                if (c > 255) {
                    return false;
                }
                if (map.mapping[c] instanceof KeyMap) {
                    if (i == keySeq.length() - 1) {
                        return map.mapping[c] != null;
                    } else {
                        map = (KeyMap) map.mapping[c];
                    }
                } else {
                    return map.mapping[c] != null;
                }
            }
        }
        return false;
    }

    public Map<String, Object> getBoundKeys() {
        Map<String, Object> bound = new TreeMap<>();
        doGetBoundKeys(this, "", bound);
        return bound;
    }

    private static void doGetBoundKeys(KeyMap keyMap, String prefix, Map<String, Object> bound) {
        if (keyMap.anotherKey != null) {
            bound.put(prefix, keyMap.anotherKey);
        }
        for (int c = 0; c < keyMap.mapping.length; c++) {
            if (keyMap.mapping[c] instanceof KeyMap) {
                doGetBoundKeys((KeyMap) keyMap.mapping[c],
                        prefix + (char) (c),
                        bound);
            } else if (keyMap.mapping[c] != null) {
                bound.put(prefix + (char) (c), keyMap.mapping[c]);
            }
        }
    }

    public Object getBound(CharSequence keySeq, int[] remaining) {
        remaining[0] = -1;
        if (keySeq != null && keySeq.length() > 0) {
            char c = keySeq.charAt(0);
            if (c > 255) {
                remaining[0] = keySeq.length() - (Character.isHighSurrogate(c) ? 2 : 1);
                return Operation.SELF_INSERT;
            } else {
                if (mapping[c] instanceof KeyMap) {
                    CharSequence sub = keySeq.subSequence(1, keySeq.length());
                    return ((KeyMap) mapping[c]).getBound(sub, remaining);
                } else if (mapping[c] != null) {
                    remaining[0] = keySeq.length() - 1;
                    return mapping[c];
                } else {
                    remaining[0] = keySeq.length();
                    return anotherKey;
                }
            }
        } else {
            return null;
        }
    }

    public Object getBound(CharSequence keySeq) {
        if (keySeq != null && keySeq.length() > 0) {
            KeyMap map = this;
            for (int i = 0; i < keySeq.length(); i++) {
                char c = keySeq.charAt(i);
                if (c > 255) {
                    return Operation.SELF_INSERT;
                }
                if (map.mapping[c] instanceof KeyMap) {
                    if (i == keySeq.length() - 1) {
                        return map.mapping[c];
                    } else {
                        map = (KeyMap) map.mapping[c];
                    }
                } else {
                    return map.mapping[c];
                }
            }
        }
        return null;
    }

    public void bindIfNotBound(CharSequence keySeq, Object function) {

        bind(this, keySeq, function, true);
    }

    public void bind(CharSequence keySeq, Object function) {

        bind(this, keySeq, function, false);
    }

    private static void bind(KeyMap map, CharSequence keySeq, Object function) {

        bind(map, keySeq, function, false);
    }

    private static void bind(KeyMap map, CharSequence keySeq, Object function,
                             boolean onlyIfNotBound) {

        if (keySeq != null && keySeq.length() > 0) {
            for (int i = 0; i < keySeq.length(); i++) {
                char c = keySeq.charAt(i);
                if (c >= map.mapping.length) {
                    return;
                }
                if (i < keySeq.length() - 1) {
                    if (!(map.mapping[c] instanceof KeyMap)) {
                        KeyMap m = new KeyMap("anonymous");
                        if (map.mapping[c] != Operation.DO_LOWERCASE_VERSION) {
                            m.anotherKey = map.mapping[c];
                        }
                        map.mapping[c] = m;
                    }
                    map = (KeyMap) map.mapping[c];
                } else {
                    if (function == null) {
                        function = NULL_FUNCTION;
                    }
                    if (map.mapping[c] instanceof KeyMap) {
                        map.anotherKey = function;
                    } else {
                        Object op = map.mapping[c];
                        if (!onlyIfNotBound
                                || op == null
                                || op == Operation.DO_LOWERCASE_VERSION
                                || op == Operation.VI_MOVEMENT_MODE) {
                            map.mapping[c] = function;
                        }
                    }
                }
            }
        }
    }

    public void setBlinkMatchingParen(boolean on) {
        if (on) {
            bind("}", Operation.INSERT_CLOSE_CURLY);
            bind(")", Operation.INSERT_CLOSE_PAREN);
            bind("]", Operation.INSERT_CLOSE_SQUARE);
        }
    }

    // TODO: use terminal capabilities
    public static void bindArrowKeys(KeyMap map) {

        // MS-DOS
        bind(map, "\033[0A", Operation.UP_LINE_OR_HISTORY);
        bind(map, "\033[0B", Operation.BACKWARD_CHAR);
        bind(map, "\033[0C", Operation.FORWARD_CHAR);
        bind(map, "\033[0D", Operation.DOWN_LINE_OR_HISTORY);

        // Windows
        bind(map, "\340\000", Operation.KILL_WHOLE_LINE);
        bind(map, "\340\107", Operation.BEGINNING_OF_LINE);
        bind(map, "\340\110", Operation.UP_LINE_OR_HISTORY);
        bind(map, "\340\111", Operation.BEGINNING_OF_HISTORY);
        bind(map, "\340\113", Operation.BACKWARD_CHAR);
        bind(map, "\340\115", Operation.FORWARD_CHAR);
        bind(map, "\340\117", Operation.END_OF_LINE);
        bind(map, "\340\120", Operation.DOWN_LINE_OR_HISTORY);
        bind(map, "\340\121", Operation.END_OF_HISTORY);
        bind(map, "\340\122", Operation.OVERWRITE_MODE);
        bind(map, "\340\123", Operation.DELETE_CHAR);

        bind(map, "\000\107", Operation.BEGINNING_OF_LINE);
        bind(map, "\000\110", Operation.UP_LINE_OR_HISTORY);
        bind(map, "\000\111", Operation.BEGINNING_OF_HISTORY);
        bind(map, "\000\110", Operation.UP_LINE_OR_HISTORY);
        bind(map, "\000\113", Operation.BACKWARD_CHAR);
        bind(map, "\000\115", Operation.FORWARD_CHAR);
        bind(map, "\000\117", Operation.END_OF_LINE);
        bind(map, "\000\120", Operation.DOWN_LINE_OR_HISTORY);
        bind(map, "\000\121", Operation.END_OF_HISTORY);
        bind(map, "\000\122", Operation.OVERWRITE_MODE);
        bind(map, "\000\123", Operation.DELETE_CHAR);

        bind(map, "\033[A", Operation.UP_LINE_OR_HISTORY);
        bind(map, "\033[B", Operation.DOWN_LINE_OR_HISTORY);
        bind(map, "\033[C", Operation.FORWARD_CHAR);
        bind(map, "\033[D", Operation.BACKWARD_CHAR);
        bind(map, "\033[H", Operation.BEGINNING_OF_LINE);
        bind(map, "\033[F", Operation.END_OF_LINE);

        bind(map, "\033OA", Operation.UP_LINE_OR_HISTORY);
        bind(map, "\033OB", Operation.DOWN_LINE_OR_HISTORY);
        bind(map, "\033OC", Operation.FORWARD_CHAR);
        bind(map, "\033OD", Operation.BACKWARD_CHAR);
        bind(map, "\033OH", Operation.BEGINNING_OF_LINE);
        bind(map, "\033OF", Operation.END_OF_LINE);

        bind(map, "\033[1~", Operation.BEGINNING_OF_LINE);
        bind(map, "\033[4~", Operation.END_OF_LINE);
        bind(map, "\033[3~", Operation.DELETE_CHAR);

        // MINGW32
        bind(map, "\0340H", Operation.UP_LINE_OR_HISTORY);
        bind(map, "\0340P", Operation.DOWN_LINE_OR_HISTORY);
        bind(map, "\0340M", Operation.FORWARD_CHAR);
        bind(map, "\0340K", Operation.BACKWARD_CHAR);
    }

//    public boolean isConvertMetaCharsToAscii() {
//        return convertMetaCharsToAscii;
//    }

//    public void setConvertMetaCharsToAscii(boolean convertMetaCharsToAscii) {
//        this.convertMetaCharsToAscii = convertMetaCharsToAscii;
//    }

    public static boolean isMeta(char c) {
        return c > 0x7f && c <= 0xff;
    }

    public static char unMeta(char c) {
        return (char) (c & 0x7F);
    }

    public static char meta(char c) {
        return (char) (c | 0x80);
    }

    public static Map<String, KeyMap> keyMaps() {
        Map<String, KeyMap> keyMaps = new HashMap<String, KeyMap>();

        KeyMap emacs = emacs();
        bindArrowKeys(emacs);
        keyMaps.put(EMACS, emacs);
        keyMaps.put(EMACS_STANDARD, emacs);
        keyMaps.put(EMACS_CTLX, (KeyMap) emacs.getBound("\u0018"));
        keyMaps.put(EMACS_META, (KeyMap) emacs.getBound("\u001b"));

        KeyMap viMov = viMovement();
        bindArrowKeys(viMov);
        keyMaps.put(VI_MOVE, viMov);
        keyMaps.put("vi-command", viMov);
        keyMaps.put("vi", viMov);

        KeyMap viIns = viInsertion();
        bindArrowKeys(viIns);
        keyMaps.put(VI_INSERT, viIns);

        KeyMap menuSelect = menuSelect();
        bindArrowKeys(menuSelect);
        keyMaps.put(MENU_SELECT, menuSelect);

        return keyMaps;
    }

    public static KeyMap emacs() {
        Object[] map = new Object[KEYMAP_LENGTH];
        Object[] ctrl = new Object[]{
                // Control keys.
                Operation.SET_MARK,                 /* Control-@ */
                Operation.BEGINNING_OF_LINE,        /* Control-A */
                Operation.BACKWARD_CHAR,            /* Control-B */
                Operation.INTERRUPT,                /* Control-C */
                Operation.DELETE_CHAR_OR_LIST,      /* Control-D */
                Operation.END_OF_LINE,              /* Control-E */
                Operation.FORWARD_CHAR,             /* Control-F */
                Operation.ABORT,                    /* Control-G */
                Operation.BACKWARD_DELETE_CHAR,     /* Control-H */
                Operation.COMPLETE_WORD,            /* Control-I */
                Operation.ACCEPT_LINE,              /* Control-J */
                Operation.KILL_LINE,                /* Control-K */
                Operation.CLEAR_SCREEN,             /* Control-L */
                Operation.ACCEPT_LINE,              /* Control-M */
                Operation.NEXT_HISTORY,             /* Control-N */
                null,                               /* Control-O */
                Operation.PREVIOUS_HISTORY,         /* Control-P */
                Operation.QUOTED_INSERT,            /* Control-Q */
                Operation.REVERSE_SEARCH_HISTORY,   /* Control-R */
                Operation.FORWARD_SEARCH_HISTORY,   /* Control-S */
                Operation.TRANSPOSE_CHARS,          /* Control-T */
                Operation.UNIX_LINE_DISCARD,        /* Control-U */
                Operation.QUOTED_INSERT,            /* Control-V */
                Operation.UNIX_WORD_RUBOUT,         /* Control-W */
                emacsCtrlX(),                       /* Control-X */
                Operation.YANK,                     /* Control-Y */
                null,                               /* Control-Z */
                emacsMeta(),                        /* Control-[ */
                null,                               /* Control-\ */
                Operation.CHARACTER_SEARCH,         /* Control-] */
                null,                               /* Control-^ */
                Operation.UNDO,                     /* Control-_ */
        };
        System.arraycopy(ctrl, 0, map, 0, ctrl.length);
        for (int i = 32; i < 256; i++) {
            map[i] = Operation.SELF_INSERT;
        }
        map[DELETE] = Operation.BACKWARD_DELETE_CHAR;
        return new KeyMap(EMACS, map);
    }

    public static final char CTRL_D = (char) 4;
    public static final char CTRL_G = (char) 7;
    public static final char CTRL_H = (char) 8;
    public static final char CTRL_I = (char) 9;
    public static final char CTRL_J = (char) 10;
    public static final char CTRL_M = (char) 13;
    public static final char CTRL_R = (char) 18;
    public static final char CTRL_S = (char) 19;
    public static final char CTRL_U = (char) 21;
    public static final char CTRL_X = (char) 24;
    public static final char CTRL_Y = (char) 25;
    public static final char ESCAPE = (char) 27; /* Ctrl-[ */
    public static final char CTRL_OB = (char) 27; /* Ctrl-[ */
    public static final char CTRL_CB = (char) 29; /* Ctrl-] */

    public static final int DELETE = (char) 127;

    public static KeyMap emacsCtrlX() {
        Object[] map = new Object[KEYMAP_LENGTH];
        map[CTRL_G] = Operation.ABORT;
        map[CTRL_U] = Operation.UNDO;
        map[CTRL_X] = Operation.EXCHANGE_POINT_AND_MARK;
        map['('] = Operation.START_KBD_MACRO;
        map[')'] = Operation.END_KBD_MACRO;
        for (int i = 'A'; i <= 'Z'; i++) {
            map[i] = Operation.DO_LOWERCASE_VERSION;
        }
        map['e'] = Operation.CALL_LAST_KBD_MACRO;
        map[DELETE] = Operation.KILL_LINE;
        return new KeyMap(EMACS_CTLX, map);
    }

    public static KeyMap emacsMeta() {
        Object[] map = new Object[KEYMAP_LENGTH];
        map[CTRL_G] = Operation.ABORT;
        map[CTRL_H] = Operation.BACKWARD_KILL_WORD;
        map[CTRL_I] = Operation.TAB_INSERT;
        map[CTRL_J] = Operation.VI_EDITING_MODE;
        map[CTRL_M] = Operation.SELF_INSERT_UNMETA;
        map[CTRL_R] = Operation.REVERT_LINE;
        map[CTRL_Y] = Operation.YANK_NTH_ARG;
        map[CTRL_OB] = Operation.COMPLETE_WORD;
        map[CTRL_CB] = Operation.CHARACTER_SEARCH_BACKWARD;
        map[' '] = Operation.SET_MARK;
        map['#'] = Operation.INSERT_COMMENT;
        map['&'] = Operation.TILDE_EXPAND;
        map['*'] = Operation.INSERT_COMPLETIONS;
        map['-'] = Operation.DIGIT_ARGUMENT;
        map['.'] = Operation.YANK_LAST_ARG;
        map['<'] = Operation.BEGINNING_OF_HISTORY;
        map['='] = Operation.POSSIBLE_COMPLETIONS;
        map['>'] = Operation.END_OF_HISTORY;
        map['?'] = Operation.POSSIBLE_COMPLETIONS;
        for (int i = 'A'; i <= 'Z'; i++) {
            map[i] = Operation.DO_LOWERCASE_VERSION;
        }
        map['\\'] = Operation.DELETE_HORIZONTAL_SPACE;
        map['_'] = Operation.YANK_LAST_ARG;
        map['b'] = Operation.BACKWARD_WORD;
        map['c'] = Operation.CAPITALIZE_WORD;
        map['d'] = Operation.KILL_WORD;
        map['f'] = Operation.FORWARD_WORD;
        map['l'] = Operation.DOWNCASE_WORD;
        map['p'] = Operation.NON_INCREMENTAL_REVERSE_SEARCH_HISTORY;
        map['r'] = Operation.REVERT_LINE;
        map['t'] = Operation.TRANSPOSE_WORDS;
        map['u'] = Operation.UPCASE_WORD;
        map['y'] = Operation.YANK_POP;
        map['~'] = Operation.TILDE_EXPAND;
        map[DELETE] = Operation.BACKWARD_KILL_WORD;
        return new KeyMap(EMACS_META, map);
    }

    public static KeyMap viInsertion() {
        Object[] map = new Object[KEYMAP_LENGTH];
        Object[] ctrl = new Object[]{
                // Control keys.
                null,                               /* Control-@ */
                Operation.SELF_INSERT,              /* Control-A */
                Operation.SELF_INSERT,              /* Control-B */
                Operation.SELF_INSERT,              /* Control-C */
                Operation.VI_EOF_MAYBE,             /* Control-D */
                Operation.SELF_INSERT,              /* Control-E */
                Operation.SELF_INSERT,              /* Control-F */
                Operation.SELF_INSERT,              /* Control-G */
                Operation.BACKWARD_DELETE_CHAR,     /* Control-H */
                Operation.COMPLETE_WORD,            /* Control-I */
                Operation.ACCEPT_LINE,              /* Control-J */
                Operation.SELF_INSERT,              /* Control-K */
                Operation.SELF_INSERT,              /* Control-L */
                Operation.ACCEPT_LINE,              /* Control-M */
                Operation.MENU_COMPLETE,            /* Control-N */
                Operation.SELF_INSERT,              /* Control-O */
                Operation.REVERSE_MENU_COMPLETE,    /* Control-P */
                Operation.SELF_INSERT,              /* Control-Q */
                Operation.REVERSE_SEARCH_HISTORY,   /* Control-R */
                Operation.FORWARD_SEARCH_HISTORY,   /* Control-S */
                Operation.TRANSPOSE_CHARS,          /* Control-T */
                Operation.UNIX_LINE_DISCARD,        /* Control-U */
                Operation.QUOTED_INSERT,            /* Control-V */
                Operation.UNIX_WORD_RUBOUT,         /* Control-W */
                Operation.SELF_INSERT,              /* Control-X */
                Operation.YANK,                     /* Control-Y */
                Operation.SELF_INSERT,              /* Control-Z */
                Operation.VI_MOVEMENT_MODE,         /* Control-[ */
                Operation.SELF_INSERT,              /* Control-\ */
                Operation.SELF_INSERT,              /* Control-] */
                Operation.SELF_INSERT,              /* Control-^ */
                Operation.UNDO,                     /* Control-_ */
        };
        System.arraycopy(ctrl, 0, map, 0, ctrl.length);
        for (int i = 32; i < 256; i++) {
            map[i] = Operation.SELF_INSERT;
        }
        map[DELETE] = Operation.BACKWARD_DELETE_CHAR;
        return new KeyMap(VI_INSERT, map);
    }

    public static KeyMap viMovement() {
        Object[] map = new Object[KEYMAP_LENGTH];
        Object[] low = new Object[]{
                // Control keys.
                null,                               /* Control-@ */
                null,                               /* Control-A */
                null,                               /* Control-B */
                Operation.INTERRUPT,                /* Control-C */
                        /* 
                         * ^D is supposed to move down half a screen. In bash
                         * appears to be ignored.
                         */
                Operation.VI_EOF_MAYBE,             /* Control-D */
                Operation.EMACS_EDITING_MODE,       /* Control-E */
                null,                               /* Control-F */
                Operation.ABORT,                    /* Control-G */
                Operation.BACKWARD_CHAR,            /* Control-H */
                null,                               /* Control-I */
                Operation.VI_MOVE_ACCEPT_LINE,      /* Control-J */
                Operation.KILL_LINE,                /* Control-K */
                Operation.CLEAR_SCREEN,             /* Control-L */
                Operation.VI_MOVE_ACCEPT_LINE,      /* Control-M */
                Operation.VI_NEXT_HISTORY,          /* Control-N */
                null,                               /* Control-O */
                Operation.VI_PREVIOUS_HISTORY,      /* Control-P */
                        /*
                         * My testing with readline is the ^Q is ignored. 
                         * Maybe this should be null?
                         */
                Operation.QUOTED_INSERT,            /* Control-Q */
                        
                        /*
                         * TODO - Very broken.  While in forward/reverse 
                         * history search the VI keyset should go out the
                         * window and we need to enter a very simple keymap.
                         */
                Operation.REVERSE_SEARCH_HISTORY,   /* Control-R */
                        /* TODO */
                Operation.FORWARD_SEARCH_HISTORY,   /* Control-S */
                Operation.TRANSPOSE_CHARS,          /* Control-T */
                Operation.UNIX_LINE_DISCARD,        /* Control-U */
                        /* TODO */
                Operation.QUOTED_INSERT,            /* Control-V */
                Operation.UNIX_WORD_RUBOUT,         /* Control-W */
                null,                               /* Control-X */
                        /* TODO */
                Operation.YANK,                     /* Control-Y */
                null,                               /* Control-Z */
                emacsMeta(),                        /* Control-[ */
                null,                               /* Control-\ */
                        /* TODO */
                Operation.CHARACTER_SEARCH,         /* Control-] */
                null,                               /* Control-^ */
                        /* TODO */
                Operation.UNDO,                     /* Control-_ */
                Operation.FORWARD_CHAR,             /* SPACE */
                null,                               /* ! */
                null,                               /* " */
                Operation.VI_INSERT_COMMENT,        /* # */
                Operation.END_OF_LINE,              /* $ */
                Operation.VI_MATCH,                 /* % */
                Operation.VI_TILDE_EXPAND,          /* & */
                null,                               /* ' */
                null,                               /* ( */
                null,                               /* ) */
                        /* TODO */
                Operation.VI_COMPLETE,              /* * */
                Operation.VI_NEXT_HISTORY,          /* + */
                Operation.VI_CHAR_SEARCH,           /* , */
                Operation.VI_PREVIOUS_HISTORY,      /* - */
                        /* TODO */
                Operation.VI_REDO,                  /* . */
                Operation.VI_SEARCH,                /* / */
                Operation.VI_BEGINNING_OF_LINE_OR_ARG_DIGIT, /* 0 */
                Operation.VI_ARG_DIGIT,             /* 1 */
                Operation.VI_ARG_DIGIT,             /* 2 */
                Operation.VI_ARG_DIGIT,             /* 3 */
                Operation.VI_ARG_DIGIT,             /* 4 */
                Operation.VI_ARG_DIGIT,             /* 5 */
                Operation.VI_ARG_DIGIT,             /* 6 */
                Operation.VI_ARG_DIGIT,             /* 7 */
                Operation.VI_ARG_DIGIT,             /* 8 */
                Operation.VI_ARG_DIGIT,             /* 9 */
                null,                               /* : */
                Operation.VI_CHAR_SEARCH,           /* ; */
                null,                               /* < */
                Operation.VI_COMPLETE,              /* = */
                null,                               /* > */
                Operation.VI_SEARCH,                /* ? */
                null,                               /* @ */
                Operation.VI_APPEND_EOL,            /* A */
                Operation.VI_PREV_WORD,             /* B */
                Operation.VI_CHANGE_TO_EOL,         /* C */
                Operation.VI_DELETE_TO_EOL,         /* D */
                Operation.VI_END_WORD,              /* E */
                Operation.VI_CHAR_SEARCH,           /* F */
                        /* I need to read up on what this does */
                Operation.VI_FETCH_HISTORY,         /* G */
                null,                               /* H */
                Operation.VI_INSERT_BEG,            /* I */
                null,                               /* J */
                null,                               /* K */
                null,                               /* L */
                null,                               /* M */
                Operation.VI_SEARCH_AGAIN,          /* N */
                null,                               /* O */
                Operation.VI_PUT,                   /* P */
                null,                               /* Q */
                        /* TODO */
                Operation.VI_REPLACE,               /* R */
                Operation.VI_KILL_WHOLE_LINE,       /* S */
                Operation.VI_CHAR_SEARCH,           /* T */
                        /* TODO */
                Operation.REVERT_LINE,              /* U */
                null,                               /* V */
                Operation.VI_NEXT_WORD,             /* W */
                Operation.VI_RUBOUT,                /* X */
                Operation.VI_YANK_TO,               /* Y */
                null,                               /* Z */
                null,                               /* [ */
                Operation.VI_COMPLETE,              /* \ */
                null,                               /* ] */
                Operation.VI_FIRST_PRINT,           /* ^ */
                Operation.VI_YANK_ARG,              /* _ */
                Operation.VI_GOTO_MARK,             /* ` */
                Operation.VI_APPEND_MODE,           /* a */
                Operation.VI_PREV_WORD,             /* b */
                Operation.VI_CHANGE_TO,             /* c */
                Operation.VI_DELETE_TO,             /* d */
                Operation.VI_END_WORD,              /* e */
                Operation.VI_CHAR_SEARCH,           /* f */
                null,                               /* g */
                Operation.BACKWARD_CHAR,            /* h */
                Operation.VI_INSERTION_MODE,        /* i */
                Operation.NEXT_HISTORY,             /* j */
                Operation.PREVIOUS_HISTORY,         /* k */
                Operation.FORWARD_CHAR,             /* l */
                Operation.VI_SET_MARK,              /* m */
                Operation.VI_SEARCH_AGAIN,          /* n */
                null,                               /* o */
                Operation.VI_PUT,                   /* p */
                null,                               /* q */
                Operation.VI_CHANGE_CHAR,           /* r */
                Operation.VI_SUBST,                 /* s */
                Operation.VI_CHAR_SEARCH,           /* t */
                Operation.UNDO,                     /* u */
                null,                               /* v */
                Operation.VI_NEXT_WORD,             /* w */
                Operation.VI_DELETE,                /* x */
                Operation.VI_YANK_TO,               /* y */
                null,                               /* z */
                null,                               /* { */
                Operation.VI_COLUMN,                /* | */
                null,                               /* } */
                Operation.VI_CHANGE_CASE,           /* ~ */
                Operation.VI_DELETE                 /* DEL */
        };
        System.arraycopy(low, 0, map, 0, low.length);
        for (int i = 128; i < 256; i++) {
            map[i] = null;
        }
        return new KeyMap(VI_MOVE, map);
    }

    public static KeyMap menuSelect() {
        KeyMap keyMap = new KeyMap(MENU_SELECT);
        keyMap.bind("\t", Operation.MENU_COMPLETE);
        keyMap.bind("\033[Z", Operation.REVERSE_MENU_COMPLETE);
        keyMap.bind("\r", Operation.ACCEPT_LINE);
        keyMap.bind("\n", Operation.ACCEPT_LINE);
        return keyMap;
    }
}
