grammar Expr;

// === ПРАВИЛА ПАРСЕРА (Синтаксис) ===

// Главный вход: запрос должен заканчиваться концом файла (EOF)
root : selectQuery EOF ;

// Правило для SELECT: ключевое слово + какие столбцы + ключевое слово + имя класса
// + опциональные условия where и limit
selectQuery : SELECT columns FROM target=IDENTIFIER whereClause? limitClause? ;

columns : STAR | columnList;

 columnList : IDENTIFIER (',' IDENTIFIER)* ;

whereClause : WHERE expression ;

expression
    : left=expression op=AND right=expression  # AndExpr  // Приоритет ниже
    | left=expression op=OR right=expression   # OrExpr   // Приоритет самый низкий
    | '(' expression ')'                       # ParenExpr
    | condition                                # AtomExpr
    ;

// Условие: Поле + Оператор + Значение
condition : field=IDENTIFIER op=operator value=literal ;

// Список поддерживаемых операторов
operator : OP_EQ | OP_GT | OP_LT | OP_NEQ ;

// Новое правило для LIMIT
limitClause : LIMIT count=INT_LITERAL ;

// Значением может быть число или строка
literal : INT_LITERAL | STRING_LITERAL ;



// === ПРАВИЛА ЛЕКСЕРА (Слова) ===

SELECT : 'SELECT' | 'select' ;
FROM   : 'FROM'   | 'from' ;
WHERE  : 'WHERE'  | 'where' ;
LIMIT  : 'LIMIT'  | 'limit' ;

AND : 'AND' | 'and' | '&&';
OR  : 'OR'  | 'or'  | '||';
STAR   : '*' ;

// Операторы
OP_EQ  : '=' ;
OP_GT  : '>' ;
OP_LT  : '<' ;
OP_NEQ : '!=' | '<>' ;

// Имя класса: начинается с буквы, содержит буквы, цифры или подчеркивания
IDENTIFIER : [a-zA-Z_] [a-zA-Z0-9_.]* ;

// Литералы (Значения)
INT_LITERAL    : [0-9]+ ;             // Целые числа
STRING_LITERAL : '\'' .*? '\'' ;      // Строки в одинарных кавычках (lazy match)

// Пропускаем пробелы и переносы строк
WS : [ \t\r\n]+ -> skip ;