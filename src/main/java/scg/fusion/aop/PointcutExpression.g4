grammar PointcutExpression;

@lexer::header{ package scg.fusion; }
@parser::header{ package scg.fusion;  }

expression: injectPointcutExpression | executionPointcutExpression;

injectPointcutExpression: (negateInject | inject | annotatedInject | negateAnnotatedInject) injectGuards*;

negateInject: NEGATION autowire LPAREN typeExpression RPAREN;

inject: autowire LPAREN typeExpression RPAREN;

annotatedInject: AT_SIGN autowire LPAREN typeExpression RPAREN;

negateAnnotatedInject: NEGATION AT_SIGN autowire LPAREN typeExpression RPAREN;

executionPointcutExpression: (methodExecution | negateMethodExecution | annotatedMethodExecution | negateAnnotatedMethodExecution) executionGuards*;

methodExecution: execution LPAREN methodExpression RPAREN;

negateMethodExecution: NEGATION execution LPAREN methodExpression RPAREN;

annotatedMethodExecution: AT_SIGN execution LPAREN typeExpression RPAREN;

negateAnnotatedMethodExecution: NEGATION AT_SIGN execution LPAREN typeExpression RPAREN;

executionGuards: ((AND | OR) (withinGuardExpression | targetGuardExpression));

injectGuards: (AND | OR) targetGuardExpression;

targetGuardExpression: targetGuard | negateTargetGuard | annotatedTargetGuard | negateAnnotatedTargetGuard;

targetGuard: target LPAREN typeExpression RPAREN;

negateTargetGuard: NEGATION target LPAREN typeExpression RPAREN;

annotatedTargetGuard: AT_SIGN target LPAREN typeExpression RPAREN;

negateAnnotatedTargetGuard: NEGATION AT_SIGN target LPAREN typeExpression RPAREN;

withinGuardExpression: withinGuard | negateWithinGuard | annotatedWithinGuard | negateAnnotatedWithinGuard;

withinGuard: within LPAREN typeExpression RPAREN;

negateWithinGuard: NEGATION within LPAREN typeExpression RPAREN;

annotatedWithinGuard: AT_SIGN within LPAREN typeExpression RPAREN;

negateAnnotatedWithinGuard: NEGATION AT_SIGN within LPAREN typeExpression RPAREN;

typeExpression: packageName DOT typeName;

packageName: referenceType | ASTERISK;

typeName: Identifier | ASTERISK;

methodExpression: returnType methodName LPAREN parameterTypeList? RPAREN;

methodName: Identifier | INIT | ASTERISK;

returnType: voidType | referenceType | primitiveType | arrayType | ASTERISK;

parameterTypeList: ((parameterTypeVariance COMMA)* parameterTypeVariance) | (DOT DOT);

parameterTypeVariance: (parameterType SEP)* parameterType;

parameterType: referenceType | covariantReferenceType | primitiveType | arrayType | ASTERISK;

covariantReferenceType: referenceType PLUS;

arrayType: (primitiveType | referenceType) arraySuffix+;

arraySuffix: LBRACK RBRACK;

primitiveType: 'byte' | 'short' | 'char' | 'int' | 'long' | 'double' | 'float' | 'boolean';

voidType: 'void';

within: 'within';

execution: 'execution';

autowire: 'autowire';

target: 'target';

referenceType: (Identifier DOT)* Identifier;

Identifier: JavaLetter JavaLetterOrDigit*;

fragment JavaLetter
	: [a-zA-Z$_]
	| ~[\u0000-\u007F\uD800-\uDBFF]	{Character.isJavaIdentifierStart(_input.LA(-1))}?
	| [\uD800-\uDBFF] [\uDC00-\uDFFF] {Character.isJavaIdentifierStart(Character.toCodePoint((char)_input.LA(-2), (char)_input.LA(-1)))}?;

fragment JavaLetterOrDigit
	: [a-zA-Z0-9$_]
	| ~[\u0000-\u007F\uD800-\uDBFF] {Character.isJavaIdentifierPart(_input.LA(-1))}?
	| [\uD800-\uDBFF] [\uDC00-\uDFFF] {Character.isJavaIdentifierPart(Character.toCodePoint((char)_input.LA(-2), (char)_input.LA(-1)))}?;

LBRACK: '[';
RBRACK: ']';
LPAREN: '(';
RPAREN: ')';

INIT: '<init>';
AND: '&&';
OR: '||';
SEP: '|';
AT_SIGN: '@';
NEGATION: '!';
PLUS: '+';
ASTERISK: '*';
DOT: '.';
COMMA: ',';

WS  :  [ \t\r\n\u000C]+ -> skip;