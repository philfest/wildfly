package org.jboss.as.mail.extension;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.common.CommonDescriptions;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.server.operations.ServerWriteAttributeOperationHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

import java.util.Locale;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIBE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;


/**
 * @author <a href="tomaz.cerar@gmail.com">Tomaz Cerar</a>
 */
public class MailSubsystemExtension implements Extension {

    /**
     * The name space used for the {@code substystem} element
     */
    //public static final String NAMESPACE =

    /**
     * The name of our subsystem within the model.
     */
    public static final String SUBSYSTEM_NAME = "mail";

    /**
     * The parser used for parsing our subsystem
     */
    private final MailSubsystemParser parser = new MailSubsystemParser();


    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(Namespace.CURRENT.getUriString(), parser);
    }


    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME);
        registration.registerXMLElementWriter(parser);
        final ManagementResourceRegistration subsystem = registration.registerSubsystemModel(MailSubsystemProviders.SUBSYSTEM);
        subsystem.registerOperationHandler(ADD, MailSubsystemAdd.INSTANCE, MailSubsystemProviders.SUBSYSTEM_ADD, false);
        subsystem.registerOperationHandler(DESCRIBE, SubsystemDescribeHandler.INSTANCE, SubsystemDescribeHandler.INSTANCE, false, OperationEntry.EntryType.PRIVATE);
        //subsystem.registerOperationHandler(DESCRIBE, DataSourcesSubsystemDescribeHandler.INSTANCE, DataSourcesSubsystemDescribeHandler.INSTANCE, false, OperationEntry.EntryType.PRIVATE);

        final ManagementResourceRegistration mailSessions = subsystem.registerSubModel(PathElement.pathElement(ModelKeys.MAIL_SESSION), MailSubsystemProviders.MAIL_SESSION_DESC);
        mailSessions.registerOperationHandler(ADD, MailSessionAdd.INSTANCE, MailSubsystemProviders.ADD_MAIL_SESSION_DESC, false);

        mailSessions.registerReadWriteAttribute(
                ModelKeys.SMTP_SERVER,
                MailSessionReadHandler.INSTANCE,
                MailSessionWriteHandler.INSTANCE,
                AttributeAccess.Storage.CONFIGURATION
        );

        //dataSources.registerOperationHandler(REMOVE, MailSessionAdd.INSTANCE, REMOVE_DATA_SOURCE_DESC, false);




    }

    public static class MailSessionReadHandler implements OperationStepHandler {
        public static MailSessionReadHandler INSTANCE = new MailSessionReadHandler();

        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final String parameterName = operation.require(NAME).asString();

            final ModelNode submodel = context.readModel(PathAddress.EMPTY_ADDRESS);
            final ModelNode currentValue = submodel.hasDefined(parameterName) ? submodel.get(parameterName).clone() : new ModelNode();

            context.getResult().set(currentValue);

            context.completeStep();
        }
    }
     public static class MailSessionWriteHandler extends ServerWriteAttributeOperationHandler {
        public static MailSessionWriteHandler INSTANCE = new MailSessionWriteHandler();


         /**
          * Hook to allow subclasses to make runtime changes to effect the attribute value change. Runtime changes
          * should be implemented by calling {@link org.jboss.as.controller.OperationContext#addStep(org.jboss.as.controller.OperationStepHandler, org.jboss.as.controller.OperationContext.Stage) adding a new step}
          * with {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME}.
          * <p>
          * This default implementation simply returns {@code false}.
          * </p>
          *
          * @param context       the context of the operation
          * @param operation     the operation
          * @param attributeName the name of the attribute being modified
          * @param newValue      the new value for the attribute
          * @param currentValue  the existing value for the attribute
          * @return {@code true} if the server requires restart to effect the attribute
          *         value change; {@code false} if not
          */
         @Override
         protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue, ModelNode currentValue) throws OperationFailedException {
             return super.applyUpdateToRuntime(context, operation, attributeName, newValue, currentValue);
         }
     }

    /**
     * Recreate the steps to put the subsystem in the same state it was in.
     * This is used in domain mode to query the profile being used, in order to
     * get the steps needed to create the servers
     */
    private static class SubsystemDescribeHandler implements OperationStepHandler, DescriptionProvider {
        static final SubsystemDescribeHandler INSTANCE = new SubsystemDescribeHandler();

        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            //context.getResult().add(createAddSubsystemOperation());
            final ModelNode result = context.getResult();
            final PathAddress rootAddress = PathAddress.pathAddress(PathAddress.pathAddress(operation.require(OP_ADDR))
                    .getLastElement());
            final ModelNode subModel = context.readModel(PathAddress.EMPTY_ADDRESS);

            final ModelNode subsystemAdd = new ModelNode();
            subsystemAdd.get(OP).set(ADD);
            subsystemAdd.get(OP_ADDR).set(rootAddress.toModelNode());

            result.add(subsystemAdd);


            if (subModel.hasDefined(ModelKeys.MAIL_SESSION)) {
                for (final Property session : subModel.get(ModelKeys.MAIL_SESSION).asPropertyList()) {
                    final ModelNode address = rootAddress.toModelNode();
                    address.add(ModelKeys.MAIL_SESSION, session.getName());
                    final ModelNode addOperation = org.jboss.as.controller.operations.common.Util.getEmptyOperation(ADD, address);

                    addOperation.get(ModelKeys.JNDI_NAME).set(session.getValue().get(ModelKeys.JNDI_NAME).asString());
                    addOperation.get(ModelKeys.DEBUG).set(session.getValue().get(ModelKeys.DEBUG).asString());
                    addOperation.get(ModelKeys.USERNAME).set(session.getValue().get(ModelKeys.USERNAME).asString());
                    addOperation.get(ModelKeys.PASSWORD).set(session.getValue().get(ModelKeys.PASSWORD).asString());
                    addOperation.get(ModelKeys.SMTP_SERVER).set(session.getValue().get(ModelKeys.SMTP_SERVER));
                    addOperation.get(ModelKeys.IMAP_SERVER).set(session.getValue().get(ModelKeys.IMAP_SERVER));
                    addOperation.get(ModelKeys.POP3_SERVER).set(session.getValue().get(ModelKeys.POP3_SERVER));

                    result.add(addOperation);
                }
            }


            context.completeStep();
        }

        @Override
        public ModelNode getModelDescription(Locale locale) {
            return CommonDescriptions.getSubsystemDescribeOperation(locale);
        }
    }

}
