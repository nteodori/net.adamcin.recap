package net.adamcin.recap.impl;

import com.day.jcr.vault.fs.api.ProgressTrackerListener;
import com.day.jcr.vault.util.DefaultProgressListener;
import com.day.jcr.vault.util.HtmlProgressListener;
import net.adamcin.recap.Recap;
import net.adamcin.recap.RecapConstants;
import net.adamcin.recap.RecapPath;
import net.adamcin.recap.RecapSessionContext;
import net.adamcin.recap.RecapSession;
import net.adamcin.recap.RecapSessionException;
import net.adamcin.recap.RecapSourceContext;
import net.adamcin.recap.RecapUtil;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author madamcin
 * @version $Id: RecapAdapterFactory.java$
 */
@Component
@Service
@Properties({
        @Property(name = SlingConstants.PROPERTY_ADAPTABLE_CLASSES,
                classValue = {
                        Resource.class,
                        SlingHttpServletRequest.class,
                        SlingHttpServletResponse.class
                }
        ),
        @Property(name = SlingConstants.PROPERTY_ADAPTER_CLASSES,
                classValue = {
                        RecapPath.class,
                        RecapSourceContext.class,
                        RecapSession.class,
                        RecapSessionContext.class,
                        ProgressTrackerListener.class
                }
        )
})
public class RecapAdapterFactory implements AdapterFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecapAdapterFactory.class);

    @Reference
    private Recap recap;

    public <AdapterType> AdapterType getAdapter(Object adaptable, Class<AdapterType> type) {
        if (adaptable instanceof Resource) {
            return getAdapter((Resource) adaptable, type);
        } else if (adaptable instanceof SlingHttpServletRequest) {
            return getAdapter((SlingHttpServletRequest) adaptable, type);
        } else if (adaptable instanceof SlingHttpServletResponse) {
            return getAdapter((SlingHttpServletResponse) adaptable, type);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType getAdapter(Resource adaptable, Class<AdapterType> type) {
        if (type == RecapPath.class) {
            Node node = adaptable.adaptTo(Node.class);
            if (node != null) {
                try {
                    return (AdapterType) RecapPath.build(node);
                } catch (RepositoryException e) {
                    LOGGER.error("failed to adapt Resource to RecapPath", e);
                }
            }
        } else if (type == RecapSourceContext.class) {
            return (AdapterType) getSourceContext(adaptable);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType getAdapter(SlingHttpServletRequest adaptable, Class<AdapterType> type) {
        if (type == RecapSession.class) {

            RecapSessionContext context = getSessionContext(adaptable);
            if (context != null) {
                try {
                    RecapSession session = recap.initSession(adaptable.getResourceResolver(), context);

                    if ("true".equals(adaptable.getParameter(RecapConstants.RP_UPDATE))) {
                        session.setUpdate(true);
                    }

                    if ("true".equals(adaptable.getParameter(RecapConstants.RP_ONLY_NEWER))) {
                        session.setOnlyNewer(true);
                    }

                    String rpBatchSize = adaptable.getParameter(RecapConstants.RP_BATCH_SIZE);
                    if (rpBatchSize != null) {
                        try {
                            session.setBatchSize(Integer.valueOf(rpBatchSize));
                        } catch (Exception e) {
                            LOGGER.error("failed to parse batch_size parameter: " + rpBatchSize, e);
                        }
                    }

                    String rpThrottle = adaptable.getParameter(RecapConstants.RP_THROTTLE);
                    if (rpThrottle != null) {
                        try {
                            session.setThrottle(Integer.valueOf(rpThrottle));
                        } catch (Exception e) {
                            LOGGER.error("failed to parse batch_size parameter: " + rpBatchSize, e);
                        }
                    }

                    return (AdapterType) session;
                } catch (RecapSessionException e) {
                    LOGGER.error("failed to open recap session", e);
                }
            } else {
                LOGGER.error("remote host parameter not specified");
            }
        } else if (type == RecapSessionContext.class) {
            return (AdapterType) getSessionContext(adaptable);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType getAdapter(SlingHttpServletResponse adaptable, Class<AdapterType> type) {
        if (type == ProgressTrackerListener.class) {
            if ("text/html".equals(adaptable.getContentType())) {
                try {
                    return (AdapterType) new HtmlProgressListener(adaptable.getWriter());
                } catch (IOException e) {
                    LOGGER.error("failed to adapt response to listener", e);
                }
            } else {
                try {
                    return (AdapterType) new DefaultProgressListener(adaptable.getWriter());
                } catch (IOException e) {
                    LOGGER.error("failed to adapt response to listener", e);
                }
            }
        }
        return null;
    }

    public RecapSourceContext getSourceContext(Resource resource) {
        ValueMap props = resource.adaptTo(ValueMap.class);
        String rpHost = props.get(RecapConstants.PROP_REMOTE_HOST, String.class);
        if (rpHost != null) {
            RecapSourceContextImpl context = new RecapSourceContextImpl();

            context.setRemoteHost(rpHost);
            context.setRemotePort(props.get(RecapConstants.PROP_REMOTE_PORT, 0));
            context.setRemoteUsername(props.get(RecapConstants.PROP_REMOTE_USER, String.class));
            context.setRemotePassword(props.get(RecapConstants.PROP_REMOTE_PASS, String.class));
            context.setContextPath(props.get(RecapConstants.PROP_REMOTE_CONTEXT_PATH, String.class));
            context.setHttps(props.get(RecapConstants.PROP_REMOTE_IS_HTTPS, false));

            return context;
        }
        return null;
    }

    public RecapSourceContext getSourceContext(SlingHttpServletRequest request) {
        String config = request.getParameter(RecapConstants.RP_REMOTE_CONFIG);
        if (config != null) {
            Resource resource = request.getResourceResolver().getResource(config);
            if (resource != null) {
                RecapSourceContext configContext = getSourceContext(resource);
                if (configContext != null) {
                    return configContext;
                }
            }
        }

        String rpHost = request.getParameter(RecapConstants.RP_REMOTE_HOST);
        if (rpHost != null) {
            RecapSourceContextImpl context = new RecapSourceContextImpl();
            context.setRemoteHost(rpHost);

            String rpPort = request.getParameter(RecapConstants.RP_REMOTE_PORT);

            if (rpPort != null) {
                try {
                    context.setRemotePort(Integer.valueOf(rpPort));
                } catch (Exception e) {
                    LOGGER.error("[getSourceContext] failed to parse remote port parameter: " + rpPort, e);
                }
            }

            if ("true".equals(request.getParameter(RecapConstants.RP_REMOTE_IS_HTTPS))) {
                context.setHttps(true);
            }

            context.setRemoteUsername(request.getParameter(RecapConstants.RP_REMOTE_USER));
            context.setRemotePassword(request.getParameter(RecapConstants.RP_REMOTE_PASS));
            context.setContextPath(request.getParameter(RecapConstants.RP_REMOTE_CONTEXT_PATH));

            return context;
        }

        return null;
    }

    public RecapSessionContext getSessionContext(SlingHttpServletRequest request) {
        RecapSourceContext sourceContext = getSourceContext(request);
        if (sourceContext != null) {
            RecapSessionContextImpl context = new RecapSessionContextImpl(sourceContext);

            context.setStrategy(request.getParameter(RecapConstants.RP_REMOTE_STRATEGY));

            context.setSuffix(request.getRequestPathInfo().getSuffix());
            String rpSuffix = request.getParameter(RecapConstants.RP_SUFFIX);

            if (rpSuffix != null) {
                context.setSuffix(rpSuffix);
            }

            context.setSelectors(request.getRequestPathInfo().getSelectors());
            String[] rpSelectors = request.getParameterValues(RecapConstants.RP_SELECTORS);

            if (rpSelectors != null) {
                context.setSelectors(rpSelectors);
            }

            List<NameValuePair> pairs = new ArrayList<NameValuePair>();
            List<NameValuePair> qsPairs;

            try {
                qsPairs = RecapUtil.parse(new URI(request.getRequestURI()), request.getCharacterEncoding());
                for (NameValuePair pair : qsPairs) {
                    if (!RecapConstants.RESERVED_PARAMS.contains(pair.getName())) {
                        pairs.add(pair);
                    }
                }
            } catch (URISyntaxException e) {
                LOGGER.error("[getSessionContext] failed to parse request URI", e);
                qsPairs = Collections.emptyList();
            }

            Set<Map.Entry<String, RequestParameter[]>> entries = request.getRequestParameterMap().entrySet();
            if (entries != null) {
                for (Map.Entry<String, RequestParameter[]> entry : entries) {
                    String name = entry.getKey();
                    if (!RecapConstants.RESERVED_PARAMS.contains(name)) {
                        RequestParameter[] rpValues = entry.getValue();
                        if (rpValues != null) {
                            for (RequestParameter rpValue : rpValues) {
                                if (rpValue.isFormField()) {
                                    NameValuePair pair = new NameValuePair(name, rpValue.getString());
                                    if (!qsPairs.contains(pair)) {
                                        pairs.add(pair);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            context.setParameters(Collections.unmodifiableList(pairs));

            return context;
        }

        return null;
    }
}
