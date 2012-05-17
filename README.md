Tinsel
======
Selector-based HTML templates using Hiccup.

Introduction
------------

Tinsel is pretty much just Hiccup with a few extra pieces of machinery. Like
Hiccup, Tinsel does as much work as possible at macro-expansion time, so it
tries to be as efficient as possible at runtime. It's also very much inspired
by Enlive and Pure, in that there is no special language for specifying where
to receive content in the templates; the template defines where to put things
based on the properties of the nodes in the HTML tree. I prefer to write HTML
in Hiccup, but Tinsel can also read in HTML and parse it into Hiccup forms
transparently to you using [hickory](http://github.com/davidsantiago/hickory).

The main idea is to take an HTML template, with absolutely no special
template-oriented markup or scripts in it, and then specify where in the tree
of HTML nodes to make replacements and additions in order to render the
template. This logic is specified in two types of functions, selectors and
transformers. The actual mechanics are a bit more involved, but basically, a
selector is a function called on every node in the HTML tree, returning true
if and only if that node should have the corresponding transformer applied.
A transformer is another function that takes a node and returns a new node
that should be inserted in its place before final rendering.

The main new construct is the `deftemplate` macro. It takes the following
arguments:

1. A name
2. A sequence of hiccup forms defining the markup
3. An argument list (for your use in the transformers)
4. As many selector/transformer pairs as you like (they will be run one
after the other in the order given)
	
As the simplest possible example, consider

	(ns tinsel-test-drive
		(:use tinsel.core))
	
	(deftemplate simple-template [[:h1 "Just a Simple Template"]]
		[])

This generates a function called simple-template, which can be called with no
argument. When you call it, it returns the string

	user> (simple-template)
	"<h1>Just a Simple Template</h1>"

To make it actually do something interesting, let's make a page that addresses
the user by name, assuming that the web server knows the user's name when it
services the request.

	(deftemplate welcome-template [[:h1#user-welcome]]
		[user-name]
		(id= :user-welcome)
		(set-content (str "Welcome " user-name "!")))

Which outputs

	user> (welcome-template "Don Draper")
	"<h1 id=\"user-welcome\">Welcome Don Draper!</h1>"

Here we made the template take a single argument, `user-name`, which is a
string containing the user's name. We also used the `id=` selector to select
any node with id "user-welcome". Then we gave the `set-content` transformer
some code to generate the string to set the content to. Note that the
user-name argument is visible to the transformer. Note also that set-content
left the tag and attributes unchanged.

Templates can take any number of selector/transformer pairs, generating as
many changes to the document tree as you like. As a slightly longer example,
look at the template below: 

	(deftemplate medium-template
	  [[:html
	    [:head
	     [:title "Literal String"]]
	    [:body
	     [:div.example]
	     [:ul.times-table]]]]
	  [text]
	  (select (or-ancestor (tag= :head))
	          (tag= :title))
	  (set-content "Times Table for 9")
	  (has-class? :example) (set-content text)
	  (has-class? :times-table)
	  (set-content (for [n (range 1 13)]
	                 [:li n " * 9 = " (* n 9)])))

Which outputs

	user> (medium-template "9 x 9 = 81")
	"<html><head><title>Times Table for 9</title></head><body><div class=\"example\">9 x 9 = 81</div><ul class=\"times-table\"><li>1 * 9 = 9</li><li>2 * 9 = 18</li><li>3 * 9 = 27</li><li>4 * 9 = 36</li><li>5 * 9 = 45</li><li>6 * 9 = 54</li><li>7 * 9 = 63</li><li>8 * 9 = 72</li><li>9 * 9 = 81</li><li>10 * 9 = 90</li><li>11 * 9 = 99</li><li>12 * 9 = 108</li></ul></body></html>"

Now, once your templates start to get this big, you should probably stick them
in their own files and load them with functions like `html-file` or define
a var to hold their content. 

Here you can see we used three selector/transformer pairs to make the template
turn into the output we wanted. The first selector is

	(select (or-ancestor (tag= :head))
	        (tag= :title))

This makes use of the `select` combinator. `select` takes any number of other
combinators and combines their output in a search down the document tree. In
this case, it will select a `title` node that has some ancestor that is a
`head` tag. We use this selector to change the title of the page. 

The next two selectors use `has-class?` to select nodes by class and set their
content. The final rendered page reflects all the changes.

To load templates straight out of HTML, you can use the functions
`html-document` (for full HTML documents) and `html-fragment` (for
fragments you would find somewhere in the body tag that don't amount
to a full document). Both accept strings with the HTML in them, or a
Reader that will deliver the HTML. You can then just pass the return
value of either as the template argument to `deftemplate`:

```clojure
(deftemplate html-template (html-document "<html></html>")
  [arg-map]
  (tag= :html)
  (set-content [:head [:title (:msg arg-map)]]))
```

Selectors and Transformers
--------------------------

Selectors and transformers are both functions, and you can supply your own if
you like. It's important now to be very clear about what these functions are.
That said, writing them is a bit tricky, so hopefully Tinsel will provide a
variety of selectors and transformers to suit most purposes without the need
for writing custom ones. Still, should it be necessary, you can always go
ahead.

###Selectors###

A selector is a function of a **a zipper of a Hiccup vector** that returns
a zipper loc for any node it has selected. In the code above, we used
`(id= :id)` as a selector. `id=` is actually a function that returns another
function, and its return value is the function that is actually used as a
selector. `id=` is present simply for convenience. 

Another function in Tinsel is the similar `tag=` selector, which returns the
loc of any HTML nodes with the given tag. One simplistic implementation for
`tag=` could be

	(require ['clojure.zip :as 'zip])
	
	(defn tag=
		[tag]
		(fn [zip-loc]
			(if (= tag (first (zip/node zip-loc)))
				zip-loc)))

So `tag=` is a function that returns functions adapted to its argument, in
this case the tag of interest. It returns a function that takes a zipper
location and returns it if the first element of the node at the given
location is equal to the argument `tag`. The raw `zip-loc` argument is
difficult to interpret, so we need to use `zip/node` to get the Hiccup form it
points to. A Hiccup form consists of a vector where the first element is the
tag as a string, the second element is a map of attributes, and any subsequent
elements are sub-nodes. So if the `tag` argument matches the first element,
that node should be selected, and its zipper loc is returned.

Why require selectors to take zippers instead of nodes? That would be easier
but it would make it impossible to write selectors based on the node's
parents, siblings, or other factors.

Also note that selectors **don't have access to the template arguments**.
This is because selectors are run at compile-time and not at run-time, so they
do not have access to the values of the template arguments. I believe this
restriction is relatively minor, though.

Currently available selectors include

* `tag=` - Selects nodes with the given tag.
* `has-attr?` - Selects nodes that have the given attribute (any value).
* `attr=` - Selects nodes that have the given attribute with the given value.
* `id=` - Selects nodes that have the given ID.
* `has-class?` - Selects nodes that have the given class (can also have
others).
* `nth-child?` - Selects nodes that are the nth-child of their parent (and
have a parent).
* `nth-last-child?` - Selects nodes that are the nth-last-child of their
parent (and have a parent).

####Selector Combinators####

There's no reason that a selector can't take another selector as an argument,
returning a compound selector with more complex behavior. In fact, Tinsel has
several selector combinators built in, which allow you to accomplish several
types of advanced behavior. 

* `every-selector` - This combinator takes any number of selector expressions
as argument and returns a new selector that selects nodes that satisfy every
selector passed as an argument. You can think of it as an "and" selector.
* `some-selector` - Similar to `every-selector`, but selects nodes that
satisfy at least one of the argument selectors. You can think of it as an
"or" selector.
* `select` - This selector lets you search for selectors that satisfy
compound hierarchical relationships. It takes any number of selectors, and
selects nodes which have parent nodes that all satisfy the selectors on the
path from parent to child. See the medium example above. Note that by default,
the path specifies a direct parent/child relationship, but you can change
this with `or-ancestor`.
* `or-ancestor` - This selector takes a selector as argument, and returns a
selector that will select nodes for which the selector is satisfied either by
themselves or an ancestor node.

###Transformers###

A transformer is a function of a Hiccup vector that returns another Hiccup
vector that should replace it. Unlike selectors, transformers do have access
to the template's arguments (kinda, keep reading). Conceptually, transformers
are easier to think about, since they just map a Hiccup vector to another
Hiccup vector. They can be a little trickier to write, however, since they
often have to copy a lot from the input vector in order to not clobber
everything other than what they are interested in. And for another reason I'll
get into in a moment.

As a simple example, here is a possible transformer you could write to remove
the children of an HTML node.

	(deftemplate untitle-template [[:html [:h1 "Some page title"]]]
		[]
		(tag= :h1)
		(fn [node] (vector (first node) (second node))))
		
		user> (untitle-template)
		"<html><h1></h1></html>"

But I've left something out above. Transformers actually run at compile time
also, just like selectors. Their output replaces the given node in the tree
at compile-time. So, although I said that they have access to the template's
arguments, it's really only the case if you write the transformer to ensure
that is the case. This is done by making sure that any code that is given
to a transformer as an argument is inserted into the Hiccup form as quoted
code. All of the transformers built into Tinsel provide correct access to the
template arguments.

As an example, let's make a transformer to change the title of a page.

	(deftemplate retitle-template [[:html [:h1 "Some page title"]]]
		[new-title]
		(tag= :h1)
		(fn [node] (vector (first node) (second node) 'new-title)))
		
		user> (retitle-template "The new title")
		"<html><h1>The new title</h1></html>"

By keeping the argument unevaluated, this guarantees that they will be
evaluated in the context of the function that `deftemplate` builds, which has
the argument list provided to `deftemplate`. In the example above, a string
was passed in, but the user could also pass in code:

	user> (retitle-template (str "The " (+ 1 1) "nd title"))
	"<html><h1>The 2nd title</h1></html>"

So ultimately transformers can also be tricky to write. I am working to make
sure that Tinsel has a good number of transformers that will hopefully span
just about any use cases I can find, but again, if you need to write your own,
you can go ahead and do so.

Currently available transformers include

* `set-content` - Replaces the node's content with the results of the
argument.
* `append-content` - Adds the results of the argument after the node's current
content.
* `prepend-content` - Adds the results of the argument before the node's
current content.
* `set-attrs` - Adds the map argument to the node's attributes, overwriting
any that are already present.

####Transformer Combinators####

Just as there are selector combinators to create more complicated selectors
from simpler ones, it is also possible to create transformer combinators,
functions that take one or more other transformers and return a new
transformer based on the arguments.

Currently available transformer combinators are

* `accumulate` - Takes any number of transformers as argument and returns a
transformer that performs all of the transformations in order on its argument
node, using the output of the first transformation as the input to the second,
etc.

Performance
-----------
In my testing, Tinsel renders templates exactly as fast as the equivalent
Hiccup code, which is itself just a tad slower than raw string concatenation.
The results below are from 
[viewbenchmarks](http://github.com/davidsantiago/viewbenchmarks)

	hiccup
	"Elapsed time: 7.126 msecs"
	"Elapsed time: 13.655 msecs"
	"Elapsed time: 5.012 msecs"
	hiccup (type-hint)
	"Elapsed time: 8.084 msecs"
	"Elapsed time: 6.157 msecs"
	"Elapsed time: 3.573 msecs"
	str
	"Elapsed time: 5.61 msecs"
	"Elapsed time: 4.928 msecs"
	"Elapsed time: 2.902 msecs"
	tinsel
	"Elapsed time: 5.358 msecs"
	"Elapsed time: 4.577 msecs"
	"Elapsed time: 3.094 msecs"
	tinsel (type-hint)
	"Elapsed time: 5.062 msecs"
	"Elapsed time: 3.646 msecs"
	"Elapsed time: 2.989 msecs"

As you can see, Tinsel still allows type-hinting just like Hiccup (really, it
is passing on the type-hinted forms to Hiccup). However, it is important to
remember that templates can only go as fast as the code you ask them to
evaluate at run-time.

Obtaining it
------------

You can add

	[tinsel "0.4.0"]

to your project.clj, or whatever is equivalent in the build tool you use.

Bugs and Missing Features
-------------------------

I use this myself, and have run out of things I feel it is
missing. But there's always room to add more selectors and
transformers, as we come across the need. My particular usage patterns
have not made me aware of any known bugs, but they are of course in
there, and I'd like to fix them if you find them, so please let me know.

License
-------

Eclipse Public License

