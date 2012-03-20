package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Checks that properties are accessed correctly.
 * User: dcheryasov
 * Date: Jun 29, 2010 5:55:52 AM
 */
public class PyPropertyAccessInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.property.access");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    private final HashMap<Pair<PyClass, String>, Property> myPropertyCache = new HashMap<Pair<PyClass, String>, Property>();

    public Visitor(@NotNull final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      super.visitPyReferenceExpression(node);
      checkExpression(node);
    }

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
      super.visitPyTargetExpression(node);
      checkExpression(node);
    }

    private void checkExpression(PyQualifiedExpression node) {
      final PyExpression qualifier = node.getQualifier();
      if (qualifier != null) {
        final PyType type = myTypeEvalContext.getType(qualifier);
        if (type instanceof PyClassType) {
          final PyClass cls = ((PyClassType)type).getPyClass();
          final String name = node.getName();
          if (cls != null && name != null) {
            final Pair<PyClass, String> key = new Pair<PyClass, String>(cls, name);
            final Property property;
            if (myPropertyCache.containsKey(key)) {
              property = myPropertyCache.get(key);
            }
            else {
              property = cls.findProperty(name);
            }
            myPropertyCache.put(key, property); // we store nulls, too, to know that a property does not exist
            if (property != null) {
              final AccessDirection dir = AccessDirection.of(node);
              checkAccessor(node, name, dir, property);
              if (dir == AccessDirection.READ) {
                final PsiElement parent = node.getParent();
                if (parent instanceof PyAugAssignmentStatement && ((PyAugAssignmentStatement)parent).getTarget() == node) {
                  checkAccessor(node, name, AccessDirection.WRITE, property);
                }
              }
            }
          }
        }
      }
    }

    private void checkAccessor(PyExpression node, String name, AccessDirection dir, Property property) {
      final Maybe<Callable> accessor = property.getByDirection(dir);
      if (accessor.isDefined() && accessor.value() == null) {
        final String message;
        if (dir == AccessDirection.WRITE) {
          message = PyBundle.message("INSP.property.$0.cant.be.set", name);
        }
        else if (dir == AccessDirection.DELETE) {
          message = PyBundle.message("INSP.property.$0.cant.be.deleted", name);
        }
        else {
          message = PyBundle.message("INSP.property.$0.cant.be.read", name);
        }
        registerProblem(node, message);
      }
    }

  }
}
