package bump;

import ast.BlockStmt;
import ast.IfStmt;
import ast.ReturnStmt;
import ast.Stmt;
import ast.TryStmt;

import java.util.List;

final class ResolverReturnAnalysis {
    boolean definitelyReturns(List<Stmt> statements) {
        for (Stmt stmt : statements) {
            if (definitelyReturns(stmt)) {
                return true;
            }
        }
        return false;
    }

    boolean definitelyReturns(Stmt stmt) {
        if (stmt instanceof ReturnStmt) {
            return true;
        }
        if (stmt instanceof BlockStmt blockStmt) {
            return definitelyReturns(blockStmt.statements);
        }
        if (stmt instanceof IfStmt ifStmt) {
            return ifStmt.elseBranch != null
                    && definitelyReturns(ifStmt.thenBranch)
                    && definitelyReturns(ifStmt.elseBranch);
        }
        if (stmt instanceof TryStmt tryStmt) {
            if (tryStmt.finallyBlock != null && definitelyReturns(tryStmt.finallyBlock)) {
                return true;
            }
            if (tryStmt.catchBlock != null) {
                return definitelyReturns(tryStmt.tryBlock) && definitelyReturns(tryStmt.catchBlock);
            }
            return definitelyReturns(tryStmt.tryBlock);
        }
        return false;
    }
}
