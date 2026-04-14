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
		SEMICOLON=6, AND=7, COMMA=8, OR=9, LPAREN=10, RPAREN=11, COMP_FIQL=12, 
		COMP_ALT=13;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"SKIP0", "SKIP1", "ALPHA", "ESCAPED_CHAR", "UNRESERVED_STR", "SINGLE_QUOTED_STR", 
			"DOUBLE_QUOTED_STR", "SEMICOLON", "AND", "COMMA", "OR", "LPAREN", "RPAREN", 
			"COMP_FIQL", "COMP_ALT"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "' '", "'\\t'", null, null, null, "';'", "' and '", "','", "' or '", 
			"'('", "')'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "SKIP0", "SKIP1", "UNRESERVED_STR", "SINGLE_QUOTED_STR", "DOUBLE_QUOTED_STR", 
			"SEMICOLON", "AND", "COMMA", "OR", "LPAREN", "RPAREN", "COMP_FIQL", "COMP_ALT"
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
		"\u0004\u0000\rl\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002\u0001"+
		"\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004"+
		"\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007"+
		"\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b"+
		"\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0001"+
		"\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0004\u0004\u0004.\b\u0004\u000b\u0004\f\u0004/\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0003\u00055\b\u0005\u0005\u00057\b\u0005\n\u0005"+
		"\f\u0005:\t\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0003\u0006A\b\u0006\u0005\u0006C\b\u0006\n\u0006\f\u0006F\t\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001"+
		"\b\u0001\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001"+
		"\n\u0001\n\u0001\u000b\u0001\u000b\u0001\f\u0001\f\u0001\r\u0001\r\u0005"+
		"\r_\b\r\n\r\f\rb\t\r\u0001\r\u0003\re\b\r\u0001\r\u0001\r\u0001\u000e"+
		"\u0001\u000e\u0003\u000ek\b\u000e\u0000\u0000\u000f\u0001\u0001\u0003"+
		"\u0002\u0005\u0000\u0007\u0000\t\u0003\u000b\u0004\r\u0005\u000f\u0006"+
		"\u0011\u0007\u0013\b\u0015\t\u0017\n\u0019\u000b\u001b\f\u001d\r\u0001"+
		"\u0000\u0005\u0002\u0000AZaz\u0005\u0000 \"\'),,;>~~\u0002\u0000\'\'\\"+
		"\\\u0002\u0000\"\"\\\\\u0002\u0000<<>>q\u0000\u0001\u0001\u0000\u0000"+
		"\u0000\u0000\u0003\u0001\u0000\u0000\u0000\u0000\t\u0001\u0000\u0000\u0000"+
		"\u0000\u000b\u0001\u0000\u0000\u0000\u0000\r\u0001\u0000\u0000\u0000\u0000"+
		"\u000f\u0001\u0000\u0000\u0000\u0000\u0011\u0001\u0000\u0000\u0000\u0000"+
		"\u0013\u0001\u0000\u0000\u0000\u0000\u0015\u0001\u0000\u0000\u0000\u0000"+
		"\u0017\u0001\u0000\u0000\u0000\u0000\u0019\u0001\u0000\u0000\u0000\u0000"+
		"\u001b\u0001\u0000\u0000\u0000\u0000\u001d\u0001\u0000\u0000\u0000\u0001"+
		"\u001f\u0001\u0000\u0000\u0000\u0003#\u0001\u0000\u0000\u0000\u0005\'"+
		"\u0001\u0000\u0000\u0000\u0007)\u0001\u0000\u0000\u0000\t-\u0001\u0000"+
		"\u0000\u0000\u000b1\u0001\u0000\u0000\u0000\r=\u0001\u0000\u0000\u0000"+
		"\u000fI\u0001\u0000\u0000\u0000\u0011K\u0001\u0000\u0000\u0000\u0013Q"+
		"\u0001\u0000\u0000\u0000\u0015S\u0001\u0000\u0000\u0000\u0017X\u0001\u0000"+
		"\u0000\u0000\u0019Z\u0001\u0000\u0000\u0000\u001bd\u0001\u0000\u0000\u0000"+
		"\u001dh\u0001\u0000\u0000\u0000\u001f \u0005 \u0000\u0000 !\u0001\u0000"+
		"\u0000\u0000!\"\u0006\u0000\u0000\u0000\"\u0002\u0001\u0000\u0000\u0000"+
		"#$\u0005\t\u0000\u0000$%\u0001\u0000\u0000\u0000%&\u0006\u0001\u0000\u0000"+
		"&\u0004\u0001\u0000\u0000\u0000\'(\u0007\u0000\u0000\u0000(\u0006\u0001"+
		"\u0000\u0000\u0000)*\u0005\\\u0000\u0000*+\t\u0000\u0000\u0000+\b\u0001"+
		"\u0000\u0000\u0000,.\b\u0001\u0000\u0000-,\u0001\u0000\u0000\u0000./\u0001"+
		"\u0000\u0000\u0000/-\u0001\u0000\u0000\u0000/0\u0001\u0000\u0000\u0000"+
		"0\n\u0001\u0000\u0000\u000018\u0005\'\u0000\u000025\u0003\u0007\u0003"+
		"\u000035\b\u0002\u0000\u000042\u0001\u0000\u0000\u000043\u0001\u0000\u0000"+
		"\u000057\u0001\u0000\u0000\u000064\u0001\u0000\u0000\u00007:\u0001\u0000"+
		"\u0000\u000086\u0001\u0000\u0000\u000089\u0001\u0000\u0000\u00009;\u0001"+
		"\u0000\u0000\u0000:8\u0001\u0000\u0000\u0000;<\u0005\'\u0000\u0000<\f"+
		"\u0001\u0000\u0000\u0000=D\u0005\"\u0000\u0000>A\u0003\u0007\u0003\u0000"+
		"?A\b\u0003\u0000\u0000@>\u0001\u0000\u0000\u0000@?\u0001\u0000\u0000\u0000"+
		"AC\u0001\u0000\u0000\u0000B@\u0001\u0000\u0000\u0000CF\u0001\u0000\u0000"+
		"\u0000DB\u0001\u0000\u0000\u0000DE\u0001\u0000\u0000\u0000EG\u0001\u0000"+
		"\u0000\u0000FD\u0001\u0000\u0000\u0000GH\u0005\"\u0000\u0000H\u000e\u0001"+
		"\u0000\u0000\u0000IJ\u0005;\u0000\u0000J\u0010\u0001\u0000\u0000\u0000"+
		"KL\u0005 \u0000\u0000LM\u0005a\u0000\u0000MN\u0005n\u0000\u0000NO\u0005"+
		"d\u0000\u0000OP\u0005 \u0000\u0000P\u0012\u0001\u0000\u0000\u0000QR\u0005"+
		",\u0000\u0000R\u0014\u0001\u0000\u0000\u0000ST\u0005 \u0000\u0000TU\u0005"+
		"o\u0000\u0000UV\u0005r\u0000\u0000VW\u0005 \u0000\u0000W\u0016\u0001\u0000"+
		"\u0000\u0000XY\u0005(\u0000\u0000Y\u0018\u0001\u0000\u0000\u0000Z[\u0005"+
		")\u0000\u0000[\u001a\u0001\u0000\u0000\u0000\\`\u0005=\u0000\u0000]_\u0003"+
		"\u0005\u0002\u0000^]\u0001\u0000\u0000\u0000_b\u0001\u0000\u0000\u0000"+
		"`^\u0001\u0000\u0000\u0000`a\u0001\u0000\u0000\u0000ae\u0001\u0000\u0000"+
		"\u0000b`\u0001\u0000\u0000\u0000ce\u0005!\u0000\u0000d\\\u0001\u0000\u0000"+
		"\u0000dc\u0001\u0000\u0000\u0000ef\u0001\u0000\u0000\u0000fg\u0005=\u0000"+
		"\u0000g\u001c\u0001\u0000\u0000\u0000hj\u0007\u0004\u0000\u0000ik\u0005"+
		"=\u0000\u0000ji\u0001\u0000\u0000\u0000jk\u0001\u0000\u0000\u0000k\u001e"+
		"\u0001\u0000\u0000\u0000\t\u0000/48@D`dj\u0001\u0006\u0000\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}