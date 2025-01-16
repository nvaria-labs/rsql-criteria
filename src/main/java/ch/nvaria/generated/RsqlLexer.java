// Generated from RsqlLexer.g4 by ANTLR 4.13.2
package ch.nvaria.generated;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class RsqlLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		SKIP0=1, SKIP1=2, UNRESERVED_STR=3, SINGLE_QUOTED_STR=4, DOUBLE_QUOTED_STR=5, 
		AND=6, OR=7, LPAREN=8, RPAREN=9, COMP_FIQL=10, COMP_ALT=11;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"SKIP0", "SKIP1", "ALPHA", "ESCAPED_CHAR", "UNRESERVED_STR", "SINGLE_QUOTED_STR", 
			"DOUBLE_QUOTED_STR", "AND", "OR", "LPAREN", "RPAREN", "COMP_FIQL", "COMP_ALT"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "' '", "'\\t'", null, null, null, null, null, "'('", "')'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "SKIP0", "SKIP1", "UNRESERVED_STR", "SINGLE_QUOTED_STR", "DOUBLE_QUOTED_STR", 
			"AND", "OR", "LPAREN", "RPAREN", "COMP_FIQL", "COMP_ALT"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public RsqlLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "RsqlLexer.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\u0004\u0000\u000bh\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002\u0001"+
		"\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004"+
		"\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007"+
		"\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b"+
		"\u0007\u000b\u0002\f\u0007\f\u0001\u0000\u0001\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002\u0001"+
		"\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0004\u0004*\b"+
		"\u0004\u000b\u0004\f\u0004+\u0001\u0005\u0001\u0005\u0001\u0005\u0003"+
		"\u00051\b\u0005\u0005\u00053\b\u0005\n\u0005\f\u00056\t\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0003\u0006=\b\u0006"+
		"\u0005\u0006?\b\u0006\n\u0006\f\u0006B\t\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0003\u0007L\b\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0003\b"+
		"S\b\b\u0001\t\u0001\t\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0005\u000b"+
		"[\b\u000b\n\u000b\f\u000b^\t\u000b\u0001\u000b\u0003\u000ba\b\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\f\u0001\f\u0003\fg\b\f\u0000\u0000\r\u0001\u0001"+
		"\u0003\u0002\u0005\u0000\u0007\u0000\t\u0003\u000b\u0004\r\u0005\u000f"+
		"\u0006\u0011\u0007\u0013\b\u0015\t\u0017\n\u0019\u000b\u0001\u0000\u0005"+
		"\u0002\u0000AZaz\u0005\u0000 \"\'),,;>~~\u0002\u0000\'\'\\\\\u0002\u0000"+
		"\"\"\\\\\u0002\u0000<<>>o\u0000\u0001\u0001\u0000\u0000\u0000\u0000\u0003"+
		"\u0001\u0000\u0000\u0000\u0000\t\u0001\u0000\u0000\u0000\u0000\u000b\u0001"+
		"\u0000\u0000\u0000\u0000\r\u0001\u0000\u0000\u0000\u0000\u000f\u0001\u0000"+
		"\u0000\u0000\u0000\u0011\u0001\u0000\u0000\u0000\u0000\u0013\u0001\u0000"+
		"\u0000\u0000\u0000\u0015\u0001\u0000\u0000\u0000\u0000\u0017\u0001\u0000"+
		"\u0000\u0000\u0000\u0019\u0001\u0000\u0000\u0000\u0001\u001b\u0001\u0000"+
		"\u0000\u0000\u0003\u001f\u0001\u0000\u0000\u0000\u0005#\u0001\u0000\u0000"+
		"\u0000\u0007%\u0001\u0000\u0000\u0000\t)\u0001\u0000\u0000\u0000\u000b"+
		"-\u0001\u0000\u0000\u0000\r9\u0001\u0000\u0000\u0000\u000fK\u0001\u0000"+
		"\u0000\u0000\u0011R\u0001\u0000\u0000\u0000\u0013T\u0001\u0000\u0000\u0000"+
		"\u0015V\u0001\u0000\u0000\u0000\u0017`\u0001\u0000\u0000\u0000\u0019d"+
		"\u0001\u0000\u0000\u0000\u001b\u001c\u0005 \u0000\u0000\u001c\u001d\u0001"+
		"\u0000\u0000\u0000\u001d\u001e\u0006\u0000\u0000\u0000\u001e\u0002\u0001"+
		"\u0000\u0000\u0000\u001f \u0005\t\u0000\u0000 !\u0001\u0000\u0000\u0000"+
		"!\"\u0006\u0001\u0000\u0000\"\u0004\u0001\u0000\u0000\u0000#$\u0007\u0000"+
		"\u0000\u0000$\u0006\u0001\u0000\u0000\u0000%&\u0005\\\u0000\u0000&\'\t"+
		"\u0000\u0000\u0000\'\b\u0001\u0000\u0000\u0000(*\b\u0001\u0000\u0000)"+
		"(\u0001\u0000\u0000\u0000*+\u0001\u0000\u0000\u0000+)\u0001\u0000\u0000"+
		"\u0000+,\u0001\u0000\u0000\u0000,\n\u0001\u0000\u0000\u0000-4\u0005\'"+
		"\u0000\u0000.1\u0003\u0007\u0003\u0000/1\b\u0002\u0000\u00000.\u0001\u0000"+
		"\u0000\u00000/\u0001\u0000\u0000\u000013\u0001\u0000\u0000\u000020\u0001"+
		"\u0000\u0000\u000036\u0001\u0000\u0000\u000042\u0001\u0000\u0000\u0000"+
		"45\u0001\u0000\u0000\u000057\u0001\u0000\u0000\u000064\u0001\u0000\u0000"+
		"\u000078\u0005\'\u0000\u00008\f\u0001\u0000\u0000\u00009@\u0005\"\u0000"+
		"\u0000:=\u0003\u0007\u0003\u0000;=\b\u0003\u0000\u0000<:\u0001\u0000\u0000"+
		"\u0000<;\u0001\u0000\u0000\u0000=?\u0001\u0000\u0000\u0000><\u0001\u0000"+
		"\u0000\u0000?B\u0001\u0000\u0000\u0000@>\u0001\u0000\u0000\u0000@A\u0001"+
		"\u0000\u0000\u0000AC\u0001\u0000\u0000\u0000B@\u0001\u0000\u0000\u0000"+
		"CD\u0005\"\u0000\u0000D\u000e\u0001\u0000\u0000\u0000EL\u0005;\u0000\u0000"+
		"FG\u0005 \u0000\u0000GH\u0005a\u0000\u0000HI\u0005n\u0000\u0000IJ\u0005"+
		"d\u0000\u0000JL\u0005 \u0000\u0000KE\u0001\u0000\u0000\u0000KF\u0001\u0000"+
		"\u0000\u0000L\u0010\u0001\u0000\u0000\u0000MS\u0005,\u0000\u0000NO\u0005"+
		" \u0000\u0000OP\u0005o\u0000\u0000PQ\u0005r\u0000\u0000QS\u0005 \u0000"+
		"\u0000RM\u0001\u0000\u0000\u0000RN\u0001\u0000\u0000\u0000S\u0012\u0001"+
		"\u0000\u0000\u0000TU\u0005(\u0000\u0000U\u0014\u0001\u0000\u0000\u0000"+
		"VW\u0005)\u0000\u0000W\u0016\u0001\u0000\u0000\u0000X\\\u0005=\u0000\u0000"+
		"Y[\u0003\u0005\u0002\u0000ZY\u0001\u0000\u0000\u0000[^\u0001\u0000\u0000"+
		"\u0000\\Z\u0001\u0000\u0000\u0000\\]\u0001\u0000\u0000\u0000]a\u0001\u0000"+
		"\u0000\u0000^\\\u0001\u0000\u0000\u0000_a\u0005!\u0000\u0000`X\u0001\u0000"+
		"\u0000\u0000`_\u0001\u0000\u0000\u0000ab\u0001\u0000\u0000\u0000bc\u0005"+
		"=\u0000\u0000c\u0018\u0001\u0000\u0000\u0000df\u0007\u0004\u0000\u0000"+
		"eg\u0005=\u0000\u0000fe\u0001\u0000\u0000\u0000fg\u0001\u0000\u0000\u0000"+
		"g\u001a\u0001\u0000\u0000\u0000\u000b\u0000+04<@KR\\`f\u0001\u0006\u0000"+
		"\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}