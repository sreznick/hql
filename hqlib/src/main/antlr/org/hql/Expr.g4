grammar Expr;

// === ПРАВИЛА ПАРСЕРА (Синтаксис) ===

// Главный вход: запрос должен заканчиваться концом файла (EOF)
root : selectQuery EOF ;

// Правило для SELECT: ключевое слово + звездочка + ключевое слово + имя класса
selectQuery : SELECT STAR FROM target=IDENTIFIER ;


// === ПРАВИЛА ЛЕКСЕРА (Слова) ===

SELECT : 'SELECT' | 'select' ;
FROM   : 'FROM'   | 'from' ;
STAR   : '*' ;

// Имя класса: начинается с буквы, содержит буквы, цифры или подчеркивания
IDENTIFIER : [a-zA-Z_] [a-zA-Z0-9_]* ;

// Пропускаем пробелы и переносы строк
WS : [ \t\r\n]+ -> skip ;