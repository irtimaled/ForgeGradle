package org.dimdev.accesstransform;

import java.util.HashMap;
import java.util.Map;

import org.dimdev.accesstransform.ElementReference.Kind;

public class AccessTransformationSet {
	public static class Transformation {
		final Map<ElementReference, AccessLevel> methods = new HashMap<>();
		final Map<ElementReference, AccessLevel> fields = new HashMap<>();
		private final String name;
		AccessLevel access;

		public Transformation(String name) {
			this.name = name;
		}

		public boolean wantsAccessChange() {
			return access != null;
		}

		public AccessLevel getAccessChange() {
			return access;
		}

		public boolean wantsMethodChange() {
			return !methods.isEmpty();
		}

		public AccessLevel popMethod(String name, String description) {
			return methods.remove(new ElementReference(Kind.METHOD, this.name, name, description));
		}

		public boolean wantsFieldChange() {
			return !fields.isEmpty();
		}

		public AccessLevel popField(String name, String description) {
			return fields.remove(new ElementReference(Kind.FIELD, this.name, name, description));
		}

		public void ensureClear() {
			if (!methods.isEmpty() || !fields.isEmpty()) {
				throw new IllegalStateException("Additional transformations to " + name + " missed: methods: " + methods + ", fields: " + fields);
			}
		}
	}
    private final Map<String, Transformation> transformations = new HashMap<>();

    public void addMimimumAccessLevel(ElementReference elementReference, AccessLevel accessLevel) {
    	switch (elementReference.kind) {
    		case CLASS: {
    			Transformation transform = transformations.computeIfAbsent(elementReference.name, Transformation::new);
    			transform.access = AccessLevel.union(transform.access, accessLevel);
				break;
    		}

    		case METHOD: {
    			Transformation transform = transformations.computeIfAbsent(elementReference.owner, Transformation::new);
    			transform.methods.put(elementReference, AccessLevel.union(transform.methods.get(elementReference), accessLevel));
				break;
    		}

    		case FIELD: {
    			Transformation transform = transformations.computeIfAbsent(elementReference.owner, Transformation::new);
    			transform.fields.put(elementReference, AccessLevel.union(transform.fields.get(elementReference), accessLevel));
				break;
    		}	
    	}
    }

    public void addMinimumAccessLevel(String string) {
        string = string.trim();
        if(string.isEmpty() || string.startsWith("#")) return;
        int indexOfFirstSpace = string.indexOf(' ');
        String accessLevel = string.substring(0, indexOfFirstSpace);
        String elementReference = string.substring(indexOfFirstSpace + 1);

        addMimimumAccessLevel(ElementReference.fromString(elementReference), AccessLevel.fromString(accessLevel));
    }

    public Transformation popTransformations(String name) {
        return transformations.remove(name);
    }

    public void ensureClear() {
    	if (!transformations.isEmpty()) {
    		throw new IllegalStateException("Additional class transformations missed for " + transformations.keySet());
    	}
    }
}
