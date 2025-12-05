package org.hql.query.ast

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.hql.ExprLexer
import org.hql.ExprParser

// 1. Превращаем класс в data class, чтобы хранить результат
data class QueryAST(
    val targetClassName: String
) {
    companion object {
        fun create(query: String): QueryAST {
            // Создаем поток символов из строки
            val charStream = CharStreams.fromString(query)

            // Лексер разбивает строку на токены (SELECT, *, FROM, String)
            val lexer = ExprLexer(charStream)
            val tokens = CommonTokenStream(lexer)

            // Парсер строит дерево на основе токенов
            val parser = ExprParser(tokens)

            // 2. Вызываем наше главное правило "root" из грамматики
            val tree = parser.root()

            // 3. Достаем данные из дерева
            // root -> selectQuery -> target (это метка из грамматики) -> text
            val className = tree.selectQuery().target.text

            return QueryAST(targetClassName = className)
        }
    }
}