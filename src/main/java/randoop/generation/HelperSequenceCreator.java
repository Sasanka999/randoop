package randoop.generation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import randoop.BugInRandoopException;
import randoop.main.GenInputsAbstract;
import randoop.operation.ConstructorCall;
import randoop.operation.MethodCall;
import randoop.operation.NonreceiverTerm;
import randoop.operation.TypedClassOperation;
import randoop.operation.TypedOperation;
import randoop.sequence.Sequence;
import randoop.sequence.Variable;
import randoop.types.ArrayType;
import randoop.types.ClassOrInterfaceType;
import randoop.types.GenericClassType;
import randoop.types.InstantiatedType;
import randoop.types.JDKTypes;
import randoop.types.JavaTypes;
import randoop.types.ParameterizedType;
import randoop.types.ReferenceArgument;
import randoop.types.ReferenceType;
import randoop.types.Type;
import randoop.types.TypeArgument;
import randoop.types.TypeTuple;
import randoop.util.ArrayListSimpleList;
import randoop.util.Randomness;
import randoop.util.SimpleList;

class HelperSequenceCreator {

  private HelperSequenceCreator() {
    throw new Error("Do not instantiate");
  }

  /**
   * Returns a sequence that creates an object of type compatible with the given
   * class. Wraps the object in a list, and returns the list.
   *
   * CURRENTLY, will return a sequence (i.e. a non-empty list) only if cls is an
   * array.
   *
   * @param components  the component manager with existing sequences
   * @param collectionType  the query type
   * @return the singleton list containing the compatible sequence
   */
  static SimpleList<Sequence> createArraySequence(
      ComponentManager components, Type collectionType) {

    final int MAX_LENGTH = 7;

    if (!collectionType.isArray()) {
      return new ArrayListSimpleList<>();
    }

    ArrayType arrayType = (ArrayType) collectionType;
    Type componentType = arrayType.getComponentType();

    SimpleList<Sequence> candidates;
    if (componentType.isArray()) {
      candidates = createArraySequence(components, componentType);
    } else {
      if (componentType.isParameterized()) {
        // XXX build elementType default construction sequence here, if cannot build one then stop
        InstantiatedType creationType = getImplementingType((InstantiatedType) componentType);
        /* If element type is C<T extends C<T>, so use T */
        if (creationType.isRecursiveType()) {
          // XXX being incautious, argument type might be parameterized
          componentType =
              ((ReferenceArgument) creationType.getTypeArguments().get(0)).getReferenceType();
        }
      }
      candidates = components.getSequencesForType(componentType);
    }

    int length;
    if (candidates.isEmpty()) {
      // No sequences that produce appropriate component values found,
      // if null allowed, create an array containing null, otherwise create empty array
      ArrayListSimpleList<Sequence> seqList = new ArrayListSimpleList<>();
      if (!GenInputsAbstract.forbid_null) {
        if (!Randomness.weightedCoinFlip(0.5)) {
          seqList.add(
              new Sequence()
                  .extend(TypedOperation.createNullOrZeroInitializationForType(componentType)));
        }
      }
      length = seqList.size();
      candidates = seqList;
    } else {
      length = Randomness.nextRandomInt(MAX_LENGTH);
    }

    Sequence elementSequence = createElementSequence(candidates, length, componentType);
    Sequence s = createAnArray(elementSequence, componentType, length);
    assert s != null;
    ArrayListSimpleList<Sequence> l = new ArrayListSimpleList<>();
    l.add(s);
    return l;
  }

  /**
   * Generates a sequence that creates a Collection.
   *
   * @param componentManager  the component manager for selecting values
   * @param collectionType  the type for collection
   * @return a collection of the given type
   */
  static Sequence createCollection(
      ComponentManager componentManager, InstantiatedType collectionType) {

    // get the element type
    List<TypeArgument> argumentList = collectionType.getTypeArguments();
    assert argumentList.size() == 1 : "Collection classes should have one type argument";
    TypeArgument argumentType = argumentList.get(0);
    ReferenceType elementType;
    assert argumentType instanceof ReferenceArgument
        : "type argument must be reference type, have " + argumentType;
    elementType = ((ReferenceArgument) argumentType).getReferenceType();

    // select implementing Collection type and instantiate
    InstantiatedType implementingType = getImplementingType(collectionType);

    SimpleList<Sequence> candidates = componentManager.getSequencesForType(elementType);
    int length = 0;
    if (!candidates.isEmpty()) {
      length = Randomness.nextRandomInt(candidates.size()) + 1;
    }
    assert !candidates.isEmpty() || length == 0 : "if there are no candidates, length must be zero";
    Sequence elementSequence = createElementSequence(candidates, length, elementType);

    // build sequence to create a Collection object
    Sequence creationSequence = createCollectionCreationSequence(implementingType, elementType);
    if (creationSequence == null) {
      return null;
    }

    if (!elementType.isParameterized()
        && !(elementType.isArray() && ((ArrayType) elementType).hasParameterizedElementType())) {
      // build sequence to create array of element type
      int totStatements = 0;
      List<Sequence> inputSequences = new ArrayList<>();
      List<Integer> variableIndices = new ArrayList<>();
      Sequence inputSequence = createAnArray(elementSequence, elementType, length);
      inputSequences.add(inputSequence);
      int inputIndex = totStatements + inputSequence.getLastVariable().index;
      totStatements += inputSequence.size();
      inputSequences.add(creationSequence);
      int creationIndex = totStatements + creationSequence.getLastVariable().index;
      variableIndices.add(creationIndex);
      variableIndices.add(inputIndex);

      // call Collections.addAll(c, inputArray)
      TypedOperation addOperation = getCollectionAddAllOperation(elementType);
      return Sequence.createSequence(addOperation, inputSequences, variableIndices);
    } else {
      final TypedOperation addOperation = getAddOperation(collectionType, elementType);
      SequenceExtender addExtender =
          new SequenceExtender() {
            @Override
            public Sequence extend(Sequence addSequence, int creationIndex, Integer index, int i) {
              List<Variable> inputs = new ArrayList<>();
              inputs.add(addSequence.getVariable(creationIndex));
              inputs.add(addSequence.getVariable(index));
              return addSequence.extend(addOperation, inputs);
            }
          };
      return buildAddSequence(creationSequence, elementSequence, addExtender);
    }
  }

  private interface SequenceExtender {
    Sequence extend(Sequence addSequence, int creationIndex, Integer index, int i);
  }

  private static Sequence buildAddSequence(
      Sequence creationSequence, Sequence elementSequence, SequenceExtender addSequenceExtender) {
    List<Sequence> inputSequences = new ArrayList<>();
    inputSequences.add(elementSequence);
    inputSequences.add(creationSequence);
    Sequence addSequence = Sequence.concatenate(inputSequences);
    int creationIndex = addSequence.getLastVariable().index;
    int i = 0;
    for (Integer index : elementSequence.getOutputIndices()) {
      addSequence = addSequenceExtender.extend(addSequence, creationIndex, index, i);
      i++;
    }
    return addSequence;
  }

  /**
   * Creates the creation sequence for a collection with the given type and element type.
   *
   * @param implementingType  the collection type
   * @param elementType  the type of the elements
   * @return a {@link Sequence} that creates a collection of {@code implementingType}
   */
  private static Sequence createCollectionCreationSequence(
      InstantiatedType implementingType, ReferenceType elementType) {
    Sequence creationSequence = new Sequence();
    List<Variable> creationInputs = new ArrayList<>();
    TypedOperation creationOperation;
    if (implementingType.isInstantiationOf(JDKTypes.ENUM_SET_TYPE)) {
      NonreceiverTerm classLiteral =
          new NonreceiverTerm(JavaTypes.CLASS_TYPE, elementType.getRuntimeClass());
      creationSequence =
          creationSequence.extend(TypedOperation.createNonreceiverInitialization(classLiteral));
      creationInputs.add(creationSequence.getLastVariable());
      creationOperation = getEnumSetCreation(implementingType);
    } else {
      Constructor<?> constructor = getDefaultConstructor(implementingType);
      if (constructor == null) {
        return null;
      }
      ConstructorCall op = new ConstructorCall(constructor);
      creationOperation =
          new TypedClassOperation(op, implementingType, new TypeTuple(), implementingType);
    }
    return creationSequence.extend(creationOperation, creationInputs);
  }

  /**
   * Creates a sequence that builds an array of the given element type using sequences from the
   * given list of candidates.
   *
   * @param elementSequence  the sequence creating element values
   * @param elementType  the type of elements for the array
   * @param length  the length of the array
   * @return a sequence that creates an array with the given element type
   */
  private static Sequence createAnArray(Sequence elementSequence, Type elementType, int length) {

    ArrayType arrayType = ArrayType.ofComponentType(elementType);
    if (!elementType.isParameterized()
        && !(elementType.isArray() && ((ArrayType) elementType).hasParameterizedElementType())) {
      TypedOperation creationOperation =
          TypedOperation.createInitializedArrayCreation(arrayType, length);
      return Sequence.createSequence(creationOperation, elementSequence);
    } else {
      Sequence createSequence = createGenericArrayCreationSequence(arrayType, length);
      final TypedOperation arrayElementAssignment =
          TypedOperation.createArrayElementAssignment(arrayType);
      SequenceExtender addExtender =
          new SequenceExtender() {
            @Override
            public Sequence extend(Sequence addSequence, int creationIndex, Integer index, int i) {
              addSequence =
                  addSequence.extend(
                      TypedOperation.createPrimitiveInitialization(JavaTypes.INT_TYPE, i));
              List<Variable> inputs = new ArrayList<>();
              inputs.add(addSequence.getVariable(creationIndex));
              inputs.add(addSequence.getLastVariable());
              inputs.add(addSequence.getVariable(index));
              return addSequence.extend(arrayElementAssignment, inputs);
            }
          };
      return buildAddSequence(createSequence, elementSequence, addExtender);
    }
  }

  /**
   * Creates a {@link Sequence} for creating an array with parameterized type.
   * Resulting code looks like {@code (ElementType[])new RawElementType[dim0]}.
   * Note that the {@code SuppressWarnings} annotation is added when the assignment with the cast
   * is output.
   *
   * @param arrayType  the type of the array
   * @param length  the length of the array to be created
   * @return the sequence to create an array with the given element type and length
   */
  private static Sequence createGenericArrayCreationSequence(ArrayType arrayType, int length) {

    ArrayType rawArrayType = arrayType.getRawTypeArray();

    Sequence creationSequence = new Sequence();

    // new RawElementType[length]
    List<Variable> input = new ArrayList<>();

    TypedOperation lengthTerm =
        TypedOperation.createNonreceiverInitialization(
            new NonreceiverTerm(JavaTypes.INT_TYPE, length));
    creationSequence = creationSequence.extend(lengthTerm, new ArrayList<Variable>());
    input.add(creationSequence.getLastVariable());

    TypedOperation creationOperation = TypedOperation.createArrayCreation(rawArrayType);
    creationSequence = creationSequence.extend(creationOperation, input);

    TypedOperation castOperation = TypedOperation.createCast(rawArrayType, arrayType);
    input = new ArrayList<>();
    input.add(creationSequence.getLastVariable());
    creationSequence = creationSequence.extend(castOperation, input);
    return creationSequence;
  }

  /**
   * Gets the default constructor for a {@link ClassOrInterfaceType}.
   * Returns null if the type has none.
   *
   * @param creationType  the class type
   * @return the reflection object for the default constructor of the given type; null, if there is none
   */
  private static Constructor<?> getDefaultConstructor(ClassOrInterfaceType creationType) {
    Constructor<?> constructor;
    try {
      constructor = creationType.getRuntimeClass().getConstructor();
    } catch (NoSuchMethodException e) {
      return null;
    }
    return constructor;
  }

  /**
   * Constructs an implementing type for an abstract subtype of {@code java.util.Collection} using
   * the {@link JDKTypes#getImplementingType(ParameterizedType)} method.
   * Otherwise, returns the given type.
   * <p>
   *   Note: this should ensure that the type has some mechanism for constructing an object
   *
   * @param elementType  the type
   * @return a non-abstract subtype of the given type, or the original type
   */
  private static InstantiatedType getImplementingType(InstantiatedType elementType) {
    InstantiatedType creationType = elementType;
    if (elementType.getGenericClassType().isSubtypeOf(JDKTypes.COLLECTION_TYPE)
        && elementType.getPackage().equals(JDKTypes.COLLECTION_TYPE.getPackage())) {
      GenericClassType implementingType = JDKTypes.getImplementingType(elementType);
      List<ReferenceType> typeArgumentList = new ArrayList<>();
      for (TypeArgument argument : elementType.getTypeArguments()) {
        assert (argument instanceof ReferenceArgument)
            : "all arguments should be ReferenceArgument";
        typeArgumentList.add(((ReferenceArgument) argument).getReferenceType());
      }
      creationType = implementingType.instantiate(typeArgumentList);
    }
    return creationType;
  }

  /**
   * Selects sequences as element values for creating a collection.
   *
   * @param candidates  the sequences from which to select
   * @param length  the number of values to select
   * @param elementType  the type of elements
   * @return a sequence with subsequences that create element values for a collection
   */
  private static Sequence createElementSequence(
      SimpleList<Sequence> candidates, int length, Type elementType) {
    List<Sequence> sequences = new ArrayList<>();
    List<Integer> variables = new ArrayList<>();
    for (int i = 0; i < length; i++) {
      Sequence sequence = candidates.get(Randomness.nextRandomInt(candidates.size()));
      sequences.add(sequence);
      Variable element = sequence.randomVariableForTypeLastStatement(elementType);
      assert element != null;
      variables.add(element.index);
    }
    return Sequence.createSequence(sequences, variables);
  }

  /**
   * Create the operation needed to create an empty EnumSet of the given type.
   *
   * @param creationType  the EnumSet type
   * @return the empty EnumSet with the given type
   */
  private static TypedOperation getEnumSetCreation(ParameterizedType creationType) {
    Class<?> enumsetClass = JDKTypes.ENUM_SET_TYPE.getRuntimeClass();
    Method method;
    try {
      method = enumsetClass.getMethod("noneOf", JavaTypes.CLASS_TYPE.getRuntimeClass());
    } catch (NoSuchMethodException e) {
      throw new BugInRandoopException(
          "Can't find \"noneOf\" method for EnumSet: " + e.getMessage());
    }
    MethodCall op = new MethodCall(method);
    List<Type> paramTypes = new ArrayList<>();
    paramTypes.add(JavaTypes.CLASS_TYPE);
    return new TypedClassOperation(op, creationType, new TypeTuple(paramTypes), creationType);
  }

  /**
   * Create a method call operation for the <code>add()</code> method of the given collection type.
   *
   * @param collectionType  the collection type
   * @param elementType  the element type of the collection
   * @return return an operation to add elements to the collection type
   */
  private static TypedOperation getAddOperation(
      ParameterizedType collectionType, ReferenceType elementType) {
    Method addMethod;
    try {
      addMethod = collectionType.getRuntimeClass().getMethod("add", Object.class);
    } catch (NoSuchMethodException e) {
      throw new BugInRandoopException(
          "Can't find add() method for " + collectionType + ": " + e.getMessage());
    }
    MethodCall op = new MethodCall(addMethod);
    List<Type> arguments = new ArrayList<>();
    arguments.add(collectionType);
    arguments.add(elementType);
    return new TypedClassOperation(
        op, collectionType, new TypeTuple(arguments), JavaTypes.BOOLEAN_TYPE);
  }

  /**
   * Create the operation to call {@link java.util.Collections#addAll(Collection, Object[])} that
   * allows initialization of a {@link Collection} object.
   *
   * @param elementType  the element type of the collection
   * @return the operation to initialize a collection from an array
   */
  private static TypedOperation getCollectionAddAllOperation(ReferenceType elementType) {
    Class<?> collectionsClass = Collections.class;
    Method method;
    try {
      method =
          collectionsClass.getMethod(
              "addAll", JDKTypes.COLLECTION_TYPE.getRuntimeClass(), (new Object[] {}).getClass());
    } catch (NoSuchMethodException e) {
      throw new BugInRandoopException("Can't find Collections.addAll method: " + e.getMessage());
    }
    MethodCall op = new MethodCall(method);
    assert method.getTypeParameters().length == 1 : "method should have one type parameter";
    List<Type> paramTypes = new ArrayList<>();
    ParameterizedType collectionType;
    collectionType = JDKTypes.COLLECTION_TYPE.instantiate(elementType);

    paramTypes.add(collectionType);
    paramTypes.add(ArrayType.ofComponentType(elementType));

    return new TypedClassOperation(
        op,
        ClassOrInterfaceType.forClass(collectionsClass),
        new TypeTuple(paramTypes),
        JavaTypes.BOOLEAN_TYPE);
  }
}
