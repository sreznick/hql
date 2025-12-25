grammar Expr;

// === ПРАВИЛА ПАРСЕРА (Синтаксис) ===

// Главный вход: запрос должен заканчиваться концом файла (EOF)
root : selectQuery EOF ;

// Правило для SELECT: ключевое слово + какие столбцы + ключевое слово + имя класса
// + опциональные условия where и limit
selectQuery : SELECT columns FROM target=className additionalClause*;

columns : STAR | columnList;

columnList : column (',' column)* ;

column : expression (AS name=IDENTIFIER)? ;

className : IDENTIFIER ('.' IDENTIFIER)* ;

additionalClause: whereClause | limitClause | offsetClause | orderClause;

whereClause : WHERE expression ;

limitClause : LIMIT count=INT_LITERAL ;

offsetClause : OFFSET count=INT_LITERAL ;

orderClause : ORDER_BY key=expression (ASC | DESC)? ;

expression
    : left=expression op=ACCESS right=IDENTIFIER     # AccessExpr
    | left=expression op=STAR right=expression       # MultExpr
    | left=expression op=DIV right=expression        # DivExpr
    | left=expression op=PLUS right=expression       # PlusExpr
    | left=expression op=MINUS right=expression      # MinusExpr
    | left=expression op=AND right=expression        # AndExpr
    | left=expression op=OR right=expression         # OrExpr
    | left=expression op=operator right=expression   # ConditionExpr
    | '(' expression ')'                             # ParenExpr
    | BOOL_LITERAL                                   # BoolLiteralExpr
    | INT_LITERAL                                    # IntLiteralExpr
    | FLOAT_LITERAL                                  # FloatLiteralExpr
    | NULL_LITERAL                                   # NullLiteralExpr
    | STRING_LITERAL                                 # StringLiteralExpr
    | IDENTIFIER                                     # IdentifierExpr
    ;

// Список поддерживаемых операторов
operator : OP_EQ | OP_GT | OP_LT | OP_GE | OP_LE | OP_NEQ ;


// === ПРАВИЛА ЛЕКСЕРА (Слова) ===

SELECT : 'SELECT' | 'select' ;
FROM   : 'FROM'   | 'from' ;
WHERE  : 'WHERE'  | 'where' ;
LIMIT  : 'LIMIT'  | 'limit' ;
AS     : 'AS'     | 'as' ;
OFFSET : 'OFFSET' | 'offset' ;
ORDER_BY : 'ORDER BY' | 'order by' ;
ASC    : 'ASC'    | 'asc' ;
DESC   : 'DESC'   | 'desc' ;

PLUS : '+';
MINUS : '-';
DIV : '/';
ACCESS : '.';

AND : 'AND' | 'and' | '&&';
OR  : 'OR'  | 'or'  | '||';
STAR  : '*' ;

// Операторы
OP_EQ  : '=' ;
OP_GT  : '>' ;
OP_LT  : '<' ;
OP_GE  : '>=' ;
OP_LE  : '<=' ;
OP_NEQ : '!=' | '<>' ;

// Литералы (Значения)
INT_LITERAL    : [+-]? [0-9]+ ;                               // Целые числа
FLOAT_LITERAL  : [+-]? [0-9]+ '.' [0-9]* ;                    // Дробные числа
BOOL_LITERAL   : 'true' ;  // Булевы значения
STRING_LITERAL : '\'' .*? '\'' ;                        // Строки в одинарных кавычках (lazy match)
NULL_LITERAL   : 'null' | 'NULL' ;                      // null значение

// Имя класса: начинается с буквы, содержит буквы, цифры или подчеркивания
IDENTIFIER : [a-zA-Z_] [a-zA-Z0-9_]* ;

// Пропускаем пробелы и переносы строк
WS : [ \t\r\n]+ -> skip ;