package org.dimdev.accesstransform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import org.dimdev.accesstransform.AccessTransformationSet.Transformation;

public class AccessTransformer {
    private final AccessTransformationSet transformations;

    public AccessTransformer(AccessTransformationSet transformations) {
        this.transformations = transformations;
    }

    public byte[] transformClass(String name, byte[] bytes) {
    	Transformation transform = transformations.popTransformations(name);
        if (bytes == null || transform == null) {
            return bytes; //Nothing to do
        }

        ClassNode clazz = new ClassNode();
        ClassReader reader = new ClassReader(bytes);
        reader.accept(clazz, 0);

        if (transform.wantsAccessChange()) {
	        // Transform class access level
	        clazz.access = getNewAccessLevel(clazz.access, transform.access);
        }

        if (transform.wantsFieldChange()) {
	        for (FieldNode field : clazz.fields) {
	        	AccessLevel access = transform.popField(field.name, field.desc);
	        	if (access != null) field.access = getNewAccessLevel(field.access, access);
	        }
        }

        if (transform.wantsMethodChange()) {
	        for (MethodNode method : clazz.methods) {
	        	AccessLevel access = transform.popMethod(method.name, method.desc);
	        	if (access != null) method.access = getNewAccessLevel(method.access, access);
	        }
        }

        //Make sure there aren't any other transformations requested for this class
        transform.ensureClear();

        ClassWriter writer = new ClassWriter(0);
        clazz.accept(writer);
        return writer.toByteArray();
    }

    private int getNewAccessLevel(int access, AccessLevel minimumAccessLevel) {
        AccessLevel.Visibility visibility;
        if ((access & Opcodes.ACC_PUBLIC) != 0)  {
            visibility = AccessLevel.Visibility.PUBLIC;
            access &= ~Opcodes.ACC_PUBLIC;
        } else if ((access & Opcodes.ACC_PROTECTED) != 0)  {
            visibility = AccessLevel.Visibility.PROTECTED;
            access &= ~Opcodes.ACC_PROTECTED;
        } else if ((access & Opcodes.ACC_PRIVATE) != 0)  {
            visibility = AccessLevel.Visibility.PRIVATE;
            access &= ~Opcodes.ACC_PRIVATE;
        } else {
            visibility = AccessLevel.Visibility.DEFAULT;
        }
        boolean isFinal = (access & Opcodes.ACC_FINAL) != 0;

        AccessLevel newAccessLevel = AccessLevel.union(minimumAccessLevel, new AccessLevel(visibility, isFinal));
        if (newAccessLevel == null) {
            return access;
        }

        if (isFinal && !newAccessLevel.isFinal) {
            access &= ~Opcodes.ACC_FINAL;
        }

        switch (newAccessLevel.visibility) {
            case PUBLIC: {
                return access | Opcodes.ACC_PUBLIC;
            }

            case PROTECTED: {
                return access | Opcodes.ACC_PROTECTED;
            }

            case DEFAULT: {
                return access;
            }

            case PRIVATE: {
                return access | Opcodes.ACC_PRIVATE;
            }

            default: {
                throw new RuntimeException("Unknown visibility level '" + newAccessLevel.visibility + "'");
            }
        }
    }
}
