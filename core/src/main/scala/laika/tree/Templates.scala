/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package laika.tree

import laika.rewrite.DocumentCursor
import laika.rewrite.TemplateRewriter
import laika.tree.Elements._

/** Provides the elements of the document tree which are specific to templates.
 *  
 *  In contrast to document trees built from text markup, these elements
 *  do not make a distinction between block and inline elements. Therefore
 *  all template elements mix in the `TemplateSpan` trait which extends
 *  `Span`, making it an inline element.
 *  
 *  The abstract base classes are not sealed as the tree model is extensible.
 *  Renderers should anticipate unknown elements and add fallback rules for those.
 *  
 *  @author Jens Halm
 */
object Templates {
  
  /** Represents a placeholder inline element that needs
   *  to be resolved in a rewrite step.
   *  Useful for elements that need access to the
   *  document, structure, title or configuration before
   *  being fully resolved.
   */
  trait SpanResolver extends Span {
    def resolve (cursor: DocumentCursor): Span
  }

  /** Represents a placeholder block element that needs
   *  to be resolved in a rewrite step.
   *  Useful for elements that need access to the
   *  document, structure, title or configuration before
   *  being fully resolved.
   */
  trait BlockResolver extends Block {
    def resolve (cursor: DocumentCursor): Block
  }
  
  /** Represents a reference to a value from the context
   *  of the current document. The `ref` attribute
   *  is a simple path expression in dot notation
   *  for accessing properties of an object (or keys
   *  of a Map).
   *  
   *  The root elements accessible to such a reference are:
   *  
   *  - `document`: the current document with all of its public properties
   *  - `parent`: the parent tree of the current document
   *  - `root`: the root tree
   *  - `config`: all configuration values for the current document, 
   *    including those inherited from parent trees
   */
  abstract class ContextReference[T <: Span] (ref: String) extends SpanResolver {
    
    def result (value: Option[Any]): T
    
    def resolve (cursor: DocumentCursor): Span = cursor.resolveReference(ref) match {
      case Some(s: ElementTraversal[_]) => result(Some(s rewrite TemplateRewriter.rewriteRules(cursor)))
      case other => result(other)
    }
  }
  
  /** A context reference specifically for use in template documents.
   */
  case class TemplateContextReference (ref: String, options: Options = NoOpt) extends ContextReference[TemplateSpan](ref) with TemplateSpan {
    
    def result (value: Option[Any]): TemplateSpan = value match {
      case Some(s: TemplateSpan)      => s
      case Some(RootElement(content)) => EmbeddedRoot(content)
      case Some(e: Element)           => TemplateElement(e)
      case Some(other)                => TemplateString(other.toString)
      case None                       => TemplateString("")
    }
  }
  
  /** A context reference specifically for use in markup documents.
   */
  case class MarkupContextReference (ref: String, options: Options = NoOpt) extends ContextReference[Span](ref) {
    
    def result (value: Option[Any]): Span = value match {
      case Some(s: Span)    => s
      case Some(e: Element) => TemplateElement(e)
      case Some(other)      => Text(other.toString)
      case None             => Text("")
    }
  }

  
  /** The base type for all inline elements that
   *  can be found in a template.
   */
  trait TemplateSpan extends Span
 
  /** Wraps a generic element that otherwise could not be placed directly into
   *  a template document tree. Useful when custom tags which are placed inside
   *  a template produce non-template tree elements.
   */
  case class TemplateElement (element: Element, indent: Int = 0, options: Options = NoOpt) extends TemplateSpan with ElementTraversal[TemplateElement]

  /** A generic container element containing a list of template spans. Can be used where a sequence
   *  of spans must be inserted in a place where a single element is required by the API.
   *  Usually renderers do not treat the container as a special element and render its children
   *  as s sub flow of the parent container.
   */
  case class TemplateSpanSequence (content: Seq[TemplateSpan], options: Options = NoOpt) extends TemplateSpan with SpanContainer[TemplateSpanSequence]

  /** A simple string element, representing the parts of a template
   *  that are not detected as special markup constructs and treated as raw text.
   */
  case class TemplateString (content: String, options: Options = NoOpt) extends TemplateSpan with TextContainer
  
  /** The root element of a template document tree.
   */
  case class TemplateRoot (content: Seq[TemplateSpan], options: Options = NoOpt) extends Block with SpanContainer[TemplateRoot]
  
  /** The root element of a document tree (originating from text markup) inside a template.
   *  Usually created by a template reference like `{{document.content}}`.
   */
  case class EmbeddedRoot (content: Seq[Block], indent: Int = 0, options: Options = NoOpt) extends TemplateSpan with BlockContainer[EmbeddedRoot]
  
}
