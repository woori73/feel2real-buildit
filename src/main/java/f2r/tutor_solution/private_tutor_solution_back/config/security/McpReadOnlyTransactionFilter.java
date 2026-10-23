package f2r.tutor_solution.private_tutor_solution_back.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Defense in depth for path-bound internal delegations; normal requests keep their existing
 * transactions.
 */
public class McpReadOnlyTransactionFilter extends OncePerRequestFilter {
  private final ObjectProvider<PlatformTransactionManager> managers;

  public McpReadOnlyTransactionFilter(ObjectProvider<PlatformTransactionManager> managers) {
    this.managers = managers;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (!Boolean.TRUE.equals(req.getAttribute("f2r.mcp.readOnly"))) {
      chain.doFilter(req, res);
      return;
    }
    var manager = managers.getIfAvailable();
    if (manager == null) {
      res.sendError(503);
      return;
    }
    var tx = new TransactionTemplate(manager);
    tx.setReadOnly(true);
    tx.setTimeout(20);
    try {
      tx.executeWithoutResult(
          status -> {
            try {
              chain.doFilter(req, res);
            } catch (IOException | ServletException e) {
              throw new DelegationException(e);
            } finally {
              status.setRollbackOnly();
            }
          });
    } catch (DelegationException e) {
      if (e.getCause() instanceof IOException io) throw io;
      throw (ServletException) e.getCause();
    }
  }

  private static class DelegationException extends RuntimeException {
    DelegationException(Exception e) {
      super(e);
    }
  }
}
