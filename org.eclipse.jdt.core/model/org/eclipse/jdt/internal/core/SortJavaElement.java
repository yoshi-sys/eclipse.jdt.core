/**********************************************************************
 * Copyright (c) 2003 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.jdt.internal.core;

import java.util.Arrays;
import java.util.Hashtable;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.internal.core.SortElementBuilder.SortElement;

/**
 * 
 * @since 2.1
 */
public abstract class SortJavaElement implements Comparable {

	static class SortElementVisitor {
		void visit(SortJavaElement node) {
		}

	}

	static class PositionsMapper extends SortElementVisitor {
		int[] positionsToMap;
		
		PositionsMapper(int[] positionsToMap) {
			this.positionsToMap = positionsToMap;
		}
		void visit(SortJavaElement node) {
			for (int i = 0, max = positionsToMap.length; i < max; i++) {
				int nextPosition = positionsToMap[i];
				if (nextPosition != -1
					&& nextPosition >= node.sourceStart 
					&& nextPosition <= node.sourceEnd) {
						node.recordPosition(nextPosition);
						positionsToMap[i] = -1;
					}
			}
		}
	}

	static class PositionsBuilder extends SortElementVisitor {
		int[] index;
		int[] positionsToMap;
		
		PositionsBuilder(int[] positionsToMap) {
			this.positionsToMap = positionsToMap;
			index = new int[1];
		}
		void visit(SortJavaElement node) {
			node.retrieveMappedPositions(this.positionsToMap, this.index);
		}
	}
	
	public static final int COMPILATION_UNIT = 1;
	public static final int TYPE = 2;
	public static final int CLASS = 4;
	public static final int INTERFACE = 8;
	public static final int FIELD = 16;
	public static final int INITIALIZER = 32;
	public static final int METHOD = 64;	
	public static final int CONSTRUCTOR = 128;
	public static final int MULTIPLE_FIELD = 256;
	
	SortElementBuilder builder;

	protected static final String LINE_SEPARATOR = System.getProperty("line.separator"); //$NON-NLS-1$
	public static final String CORRESPONDING_ELEMENT = "corresponding_element";  //$NON-NLS-1$

	Hashtable options;
	
	protected int id;
	protected int sourceStart;
	protected int newSourceStart;
	protected int modifiers;
	protected String superclass;
	protected String[] superInterfaces;
	
	protected String[] parametersNames;
	protected String[] parametersTypes;
	protected String[] thrownExceptions;
	protected String returnType;
	protected String name;
	protected String type;
	protected int fieldCounter;
	protected SortElementBuilder.SortFieldDeclaration[] innerFields;
	protected ASTNode[] astNodes;
	
	protected int sourceEnd;
	protected int nameSourceStart;
	protected SortElement[] children;
	protected int children_count;
	protected SortElement firstChildBeforeSorting;
	protected SortElement lastChildBeforeSorting;
	protected int declarationStart;
	protected int declarationSourceEnd;
	
	protected int[] positions;
	protected int positionsCounter;
	
	SortJavaElement(SortElementBuilder builder) {
		this.builder = builder;
		this.options = JavaCore.getOptions();
	} 
	/**
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		return this.builder.comparator.compare(this, o);
	}
	
	protected void addChild(SortElement sortElement) {
		if (this.children_count == 0) {
			this.children = new SortElement[3];
		} else if (this.children_count == this.children.length) {
			System.arraycopy(this.children, 0, this.children = new SortElement[this.children_count * 2], 0, this.children_count);
		}
		this.children[this.children_count++] = sortElement;
	}

	protected void closeCollections() {
		int length = this.children_count;
		if (length != 0 && length != this.children.length) {
			System.arraycopy(this.children, 0, this.children= new SortElement[length], 0, length);
		}			
	}

	abstract void display(StringBuffer buffer, int tab);
		
	protected void generateSource(StringBuffer buffer) {
		this.newSourceStart = buffer.length();
	}

	public String toString(int tab) {
		StringBuffer buffer = new StringBuffer();
		display(buffer, tab);
		if (this.children != null) {
			buffer
				.append(tab(tab))
				.append("CHILDREN ------------------------------" + LINE_SEPARATOR); //$NON-NLS-1$
			for (int i = 0; i < this.children_count; i++) {
				buffer.append(this.children[i].toString(tab + 1));
				buffer.append(LINE_SEPARATOR);
			}
		}
		return buffer.toString();
	}

	protected char[] tab(int tab) {
		char[] tabs = new char[tab];
		Arrays.fill(tabs, '\t');
		return tabs; 
	}

	public String toString() {
		return toString(0);
	}		

	protected void sort() {
		if (this.children != null) {
			this.firstChildBeforeSorting = children[0];
			this.lastChildBeforeSorting = children[this.children_count - 1];
			switch(this.id) {
				case CLASS | TYPE :
				case INTERFACE | TYPE :
				case COMPILATION_UNIT :		
					this.astNodes = convertChildren();
					Arrays.sort(astNodes, this.builder.comparator);
			}
			for (int i = 0, max = this.children_count; i < max; i++) {
				children[i].sort();
			} 
		}
	}
	
	private ASTNode[] convertChildren() {
		ASTNode[] astNodes = new ASTNode[this.children_count];
		for (int i = 0, max = this.children_count; i < max; i++) {
			SortElementBuilder.SortElement currentElement = this.children[i];
			ASTNode newNode = currentElement.convert();
			newNode.setProperty(CORRESPONDING_ELEMENT, currentElement);
			astNodes[i] = newNode;
		}
		return astNodes;
	}
	
	protected void traverseChildrenFirst(SortElementVisitor visitor) {
		if (fieldCounter != 0) {
			for (int i = 0, max = fieldCounter; i < max; i++) {
				innerFields[i].traverseChildrenFirst(visitor);
			}
		}
		if (children_count != 0) {
			for (int i = 0, max = children_count; i < max; i++) {
				children[i].traverseChildrenFirst(visitor);
			}
		}
		visitor.visit(this);
	}

	protected void traverseChildrenLast(SortElementVisitor visitor) {
		visitor.visit(this);
		if (fieldCounter != 0) {
			for (int i = 0, max = fieldCounter; i < max; i++) {
				innerFields[i].traverseChildrenLast(visitor);
			}
		}
		if (children_count != 0) {
			for (int i = 0, max = children_count; i < max; i++) {
				children[i].traverseChildrenLast(visitor);
			}
		}
	}
	
	protected void recordPosition(int position) {
		if (this.positionsCounter == 0) {
			this.positions = new int[3];
		} else if (this.positionsCounter == this.positions.length) {
			System.arraycopy(this.positions, 0, (this.positions = new int[this.positionsCounter * 2]), 0, this.positionsCounter);
		}
		this.positions[this.positionsCounter++] = position - this.sourceStart; // store the offset
//		System.out.println(this.name + " source start = " + this.sourceStart + " new source start " +  this.newSourceStart);//$NON-NLS-1$//$NON-NLS-2$
	}
	
	protected void retrieveMappedPositions(int[] mappedPositions, int[] index) {
		for (int i = 0, max = this.positionsCounter; i < max; i++) {
			mappedPositions[index[0]++] = this.positions[i] + this.newSourceStart;
		}
	}
}
