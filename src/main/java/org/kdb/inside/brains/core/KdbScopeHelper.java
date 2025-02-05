package org.kdb.inside.brains.core;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.kdb.inside.brains.UIUtils;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class KdbScopeHelper {
    private static final String ROOT_ELEMENT_NAME = "scopes";

    public Element writeScopes(List<KdbScope> scopes, boolean exportCredentials) {
        final Element element = new Element(ROOT_ELEMENT_NAME);
        scopes.forEach(s -> element.addContent(writeScope(s, exportCredentials)));
        return element;
    }

    public List<KdbScope> readScopes(Element element, ScopeType type) {
        return element.getChildren().stream().map(el -> readScope(el, type)).collect(Collectors.toList());
    }

    @NotNull
    private KdbScope readScope(@NotNull Element el, ScopeType defaultType) {
        final String name = el.getAttributeValue("name");
        ScopeType type;
        try {
            type = ScopeType.valueOf(el.getAttributeValue("type", defaultType.name()));
        } catch (Exception ex) {
            type = ScopeType.LOCAL;
        }
        final String credentials = readCredentials(el);
        final InstanceOptions options = decodeInstanceOptions(el);

        final KdbScope scope = new KdbScope(name, type, credentials, options);
        readColor(el, scope);
        readChildren(scope, el, name);

        return scope;
    }

    private void readChildren(StructuralItem item, @NotNull Element el, String parentId) {
        int i = 0;
        for (Element chEl : el.getChildren()) {
            final String name = chEl.getAttributeValue("name");
            final String id = generateNextId(parentId, name, i);

            final String elName = chEl.getName();
            if ("package".equalsIgnoreCase(elName)) {
                final PackageItem pkg = item.createPackage(name);
                readColor(chEl, pkg);
                readChildren(pkg, chEl, id);
            } else if ("instance".equalsIgnoreCase(elName)) {
                final String host = chEl.getAttributeValue("host");
                final int port = Integer.parseInt(chEl.getAttributeValue("port"));
                final InstanceOptions options = decodeInstanceOptions(chEl);
                final String credentials = readCredentials(chEl);

                final KdbInstance instance = item.createInstance(name, host, port, credentials, options);
                readColor(chEl, instance);
            }
            i++;
        }
    }

    private void readColor(Element el, InstanceItem item) {
        final String color = el.getAttributeValue("color");
        if (color == null) {
            return;
        }
        item.setColor(UIUtils.decodeColor(color));
    }

    private InstanceOptions decodeInstanceOptions(@NotNull Element el) {
        final String timeout = el.getAttributeValue("timeout");
        final String tls = el.getAttributeValue("tls");
        final String compression = el.getAttributeValue("compression");

        if (timeout == null && tls == null && compression == null) {
            return null;
        }

        final InstanceOptions o = new InstanceOptions();
        if (timeout != null) {
            o.setTimeout(Integer.parseInt(timeout));
        }
        if (tls != null) {
            o.setTls(Boolean.parseBoolean(tls));
        }
        if (compression != null) {
            o.setCompression(Boolean.parseBoolean(compression));
        }
        return o;
    }

    private String readCredentials(Element el) {
        final String credentials = el.getAttributeValue("credentials");
        if (credentials == null) {
            return null;
        }
        return new String(Base64.getDecoder().decode(credentials));
    }

    @NotNull
    private Element writeScope(KdbScope scope, boolean exportCredentials) {
        final String name = scope.getName();

        final Element scopeEl = new Element("scope");
        scopeEl.setAttribute("name", name);
        scopeEl.setAttribute("type", scope.getType().name());
        writeColor(scope, scopeEl);
        if (exportCredentials) {
            writeCredentials(scope, scopeEl);
        }
        encodeInstanceOptions(scopeEl, scope.getOptions());
        writeChildren(scope, scopeEl, name, exportCredentials);

//        writeCredentials(name, scope.getCredentials());

        return scopeEl;
    }

    private void writeChildren(StructuralItem si, Element parent, String parentId, boolean exportCredentials) {
        int i = 0;
        for (InstanceItem item : si) {
            final String name = item.getName();
            final String id = generateNextId(parentId, name, i);

            if (item instanceof PackageItem) {
                final PackageItem packageItem = (PackageItem) item;
                final Element packEl = new Element("package");
                packEl.setAttribute("name", name);
                writeColor(item, packEl);
                parent.addContent(packEl);

                writeChildren(packageItem, packEl, id, exportCredentials);
            } else if (item instanceof KdbInstance) {
                final KdbInstance instance = (KdbInstance) item;

                final Element instEl = new Element("instance");
                instEl.setAttribute("name", name);
                instEl.setAttribute("host", instance.getHost());
                instEl.setAttribute("port", String.valueOf(instance.getPort()));
                writeColor(instance, instEl);
                if (exportCredentials) {
                    writeCredentials(instance, instEl);
                }
                encodeInstanceOptions(instEl, instance.getOptions());

//                writeCredentials(id, instance.getCredentials());

                parent.addContent(instEl);
            }
            i++;
        }
    }

    private void writeColor(InstanceItem item, Element el) {
        if (item.getColor() == null) {
            return;
        }
        el.setAttribute("color", UIUtils.encodeColor(item.getColor()));
    }

    private void writeCredentials(CredentialsItem item, Element el) {
        final String credentials = item.getCredentials();
        if (credentials != null) {
            el.setAttribute("credentials", Base64.getEncoder().encodeToString(credentials.getBytes()));
        }
    }

    private void encodeInstanceOptions(@NotNull Element el, InstanceOptions options) {
        if (options == null) {
            return;
        }
        el.setAttribute("timeout", String.valueOf(options.getTimeout()));
        el.setAttribute("tls", String.valueOf(options.isTls()));
        el.setAttribute("compression", String.valueOf(options.isCompression()));
    }

    @NotNull
    private String generateNextId(String parentId, String name, int index) {
        return parentId + '/' + name + '[' + index + ']';
    }


}
