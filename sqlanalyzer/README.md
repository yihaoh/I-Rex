# High-Level Design Notes

There are two key concepts to the design of `sqlanalyzer`. 
+ Query Syntax Tree: represented in a tree of `XNode` (described later), this syntax tree is a faithful representation of the query itself. Each `XNode` in the tree stores useful information like data type, table schemas, etc. depending on the part of syntax represented by that node.
+ Debugging Context: represented by a `DContext` object, debugging context only exists for `SELECT` blocks and set operation blocks (e.g., `UNION`). Each debugging context stores information like its corresponding syntax subtree, intermediate results, data provenance, etc., which later is used for actual debugging.


Analyzer.analyzeToJson() is the main method.
The frontend is expected to call this method once with user's SQL query, and get a JSON representation of the QueryContext for that query.
Internally, the method takes the following steps:

+ First, we use Calcite to parse/validate, but then convert the validated parse tree to an XNode tree (which is simpler for us to work with).
  Key design consideration is to leverage Calcite to support as many SQL features as possible with minimal amount of additional code.
  * XNode: wraps SqlNode validated tree and provides better support for debugging
    - XCellValuedNode
      + XBasicCallNode (including aggregate and scalar subquery conversion)
      + XUsingJoinCondition (used to capture USING or implicit natural join conditions; requires special treatment --- cannot be captured by XBasicCallNode)
      + XLiteralNode
      + XColumnRefNode
      + XColumnRenameNode (only used in SELECT expr AS col)
    - XTableValuedNode
      + XSelectNode (SELECT FROM WHERE ...)
      + XJoinNode (join expression in FROM, including the cross join implied by comma)
      + XSetOpNode
      + XTableRefNode (to a database table or a table defined using WITH)
      + XTableRenameNode (only used in SELECT ... FROM ... AS tabname)
      + XWithNode (whose body provides output)
      + XWithItemNode (whose query provides output on demand)

  Supported:
  * nested, correlated WITH
  * complete JOIN syntax
  * any bulit-in function/operators
  * inference of implicit output order for any XTableValuedNode

  Still not supported:
  * CASE
  * CAST

+ Second, we extract from the XNode tree a bunch of DContexts (debugging context).
  There is one debugging context for each SQL block that we offer deubugging support:
  namely DSelectContext for XSelectNode, and DSetOpContext for XSetOpNode.
  There is a root DContext for the top-level query, but if the query contains subqueries or WITH-defined tables, they may give rise to additional DContexts.

+ We also generate a piece of code (DContextCode) for each DContext.
  Conceptually, DContextCode contains everything it takes to reproduce any intermediate result, expression evaluation and data lineages within that context.

+ Based on the query being executed, the backend returns DContextMilestoneExecd, DContextPageExecd or DContextEvalExecd.
  `DContextMilestoneExecd` has the result of executing milestone queries. `DContextPageExecd` has the result of executing page queries.
  `DContextEvalExecd` has the result of executing evaluation query for ON and WHERE expressions.

+ Note: `DSetOpContext` is not yet supported. Do not do anything with that yet.