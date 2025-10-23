package sa.com.cloudsolutions.antikythera.parser.converter;

import java.util.List;

/**
 * Interface representing a node in the HQL query Abstract Syntax Tree (AST).
 * 
 * This interface provides the basic contract for AST nodes created during
 * HQL parsing. It supports tree traversal and manipulation operations
 * needed for query conversion.
 */
public interface QueryNode {
    
    /**
     * Gets the type of this node.
     * 
     * @return The node type identifier
     */
    int getType();
    
    /**
     * Sets the type of this node.
     * 
     * @param type The node type identifier
     */
    void setType(int type);
    
    /**
     * Gets the text content of this node.
     * 
     * @return The text content
     */
    String getText();
    
    /**
     * Sets the text content of this node.
     * 
     * @param text The text content
     */
    void setText(String text);
    
    /**
     * Gets the line number where this node appears in the source.
     * 
     * @return The line number
     */
    int getLine();
    
    /**
     * Gets the column number where this node appears in the source.
     * 
     * @return The column number
     */
    int getColumn();
    
    /**
     * Gets the parent node of this node.
     * 
     * @return The parent node, or null if this is the root
     */
    QueryNode getParent();
    
    /**
     * Sets the parent node of this node.
     * 
     * @param parent The parent node
     */
    void setParent(QueryNode parent);
    
    /**
     * Gets the next sibling node.
     * 
     * @return The next sibling, or null if none
     */
    QueryNode getNextSibling();
    
    /**
     * Gets the first child node.
     * 
     * @return The first child, or null if no children
     */
    QueryNode getFirstChild();
    
    /**
     * Adds a child node to this node.
     * 
     * @param child The child node to add
     */
    void addChild(QueryNode child);
    
    /**
     * Checks if this node has any children.
     * 
     * @return true if this node has children, false otherwise
     */
    boolean hasChildren();
    
    /**
     * Gets the number of child nodes.
     * 
     * @return The number of children
     */
    int getNumberOfChildren();
    
    /**
     * Gets all child nodes.
     * 
     * @return A list of child nodes
     */
    List<QueryNode> getChildren();
    
    /**
     * Gets a specific child node by index.
     * 
     * @param index The index of the child
     * @return The child node at the specified index
     */
    QueryNode getChild(int index);
    
    /**
     * Inserts a child node at the specified index.
     * 
     * @param index The index where to insert the child
     * @param child The child node to insert
     */
    void insertChild(int index, QueryNode child);
    
    /**
     * Removes a child node.
     * 
     * @param child The child node to remove
     */
    void removeChild(QueryNode child);
    
    /**
     * Replaces an existing child node with a new one.
     * 
     * @param oldChild The child node to replace
     * @param newChild The new child node
     */
    void replaceChild(QueryNode oldChild, QueryNode newChild);
}
