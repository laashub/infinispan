package org.infinispan.commands.tx;

import static org.infinispan.util.logging.Log.CONTAINER;

import org.infinispan.commands.Visitor;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.transaction.impl.TransactionTable;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.ByteString;

/**
 * Command corresponding to the 2nd phase of 2PC.
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 * @since 4.0
 */
public class CommitCommand extends AbstractTransactionBoundaryCommand {
   public static final byte COMMAND_ID = 14;

   private CommitCommand() {
      super(null); // For command id uniqueness test
   }

   public CommitCommand(ByteString cacheName, GlobalTransaction gtx) {
      super(cacheName);
      this.globalTx = gtx;
   }

   public CommitCommand(ByteString cacheName) {
      super(cacheName);
   }

   @Override
   public Object acceptVisitor(InvocationContext ctx, Visitor visitor) throws Throwable {
      return visitor.visitCommitCommand((TxInvocationContext) ctx, this);
   }

   @Override
   protected Object invalidRemoteTxReturnValue(TransactionTable txTable) {
      TransactionTable.CompletedTransactionStatus txStatus = txTable.getCompletedTransactionStatus(globalTx);
      switch (txStatus) {
         case COMMITTED:
            // The transaction was already committed on this node
            return null;
         case ABORTED:
            throw CONTAINER.remoteTransactionAlreadyRolledBack(globalTx);
         case EXPIRED:
            throw CONTAINER.remoteTransactionStatusMissing(globalTx);
         default:  // NOT_COMPLETED
            throw new IllegalStateException("Remote transaction not found: " + globalTx);
      }
   }

   @Override
   public byte getCommandId() {
      return COMMAND_ID;
   }

   @Override
   public String toString() {
      return "CommitCommand {" + super.toString();
   }
}
