package org.hql.cli

import org.hql.query.ast.QueryAST

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        QueryAST.create(args[0])
    }
}
