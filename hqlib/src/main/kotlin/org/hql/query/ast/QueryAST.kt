package org.hql.query.ast

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

import org.hql.ExprLexer
import org.hql.ExprParser

class QueryAST {
    companion object {
        fun create(query: String): QueryAST {
            val charsStream = CharStreams.fromString(query)

            val lexer = ExprLexer(charsStream)

            val tokens = CommonTokenStream(lexer)

            val parser = ExprParser(tokens)

            // TODO: convert parse tree to AST

            return QueryAST()
        }
    }
}
