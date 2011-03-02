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
transparently to you using clj-tagsoup.

The main idea is to take an HTML template, with absolutely no special
template-oriented markup or scripts in it, and then specify where in the tree
of HTML nodes to make replacements and additions in order to render the
template. This logic is specified in two types of functions, selectors and
transformers. The actual mechanics are a bit more involved, but basically, a
selector is a function called on every node in the HTML tree, returning true
if and only if that node should have the corresponding transformer applied.
A transformer is another function that takes a node and returns a new node
that should be inserted in its place.

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

	user> (welcome-template "Don")
	"<h1 id=\"user-welcome\">Welcome Don!</h1>"

Here we made the template take a single argument, user-name, which is a string
containing the user's name. We also used the `id=` selector to select any node
with id "user-welcome". Then we gave the `set-content` transformer some code
to generate the string to set the content to. Note that the user-name argument
is visible to the transformer. Note also that set-content left the tag and
attributes unchanged.

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
true on any node it has selected. In the code above, we used `(id= :id)` as a
selector. `id=` is actually a function that returns another function, and its
return value is the function that is actually used as a selector. `id=` is
present simply for convenience. 

Another function in Tinsel is the similar `tag=` selector, which returns true
on any HTML nodes with the given tag. One simplistic implementation for `tag=`
could be

	(require ['clojure.zip :as 'zip])
	
	(defn tag=
		[tag]
		(fn [zip-loc]
			(= tag (first (zip/node zip-loc)))))

So `tag=` is a function that returns functions adapted to its argument, in
this case the tag of interest. It returns a function that takes a zipper
location and returns true if the first element of the node at the given
location is equal to the argument `tag`. The raw `zip-loc` argument is
difficult to interpret, so we need to use `zip/node` to get the Hiccup form it
points to. A Hiccup form consists of a vector where the first element is the
tag as a string, the second element is a map of attributes, and any subsequent
elements are sub-nodes. So if the `tag` argument matches the first element,
that node should be selected, and true is returned.

Why require selectors to take zippers instead of nodes? That would be easier
but it would make it impossible to write selectors based on the node's
parents, siblings, or other factors.

Also note that selectors **don't have access to the template arguments**.
This is because selectors are run at compile-time and do not run at run-time,
so they do not have access to the values of the template arguments. I believe
this restriction is relatively minor, though.

###Transformers###

A transformer is a function of a Hiccup vector that returns another Hiccup
vector that should replace it. Unlike selectors, transformers do have access
to the template's arguments and run when the template is invoked to render
output. Conceptually, transformers are easier to think about, since they just
map a Hiccup vector to another Hiccup vector. They can be a little trickier to
write, however, since they often have to copy a lot from the input vector in
order to not clobber everything other than what they are interested in.

As a simple example, here is a possible transformer you could write to remove
the children of an HTML node.

	(deftemplate untitle-template [[:html [:h1 "Some page title"]]]
		[]
		(tag= :h1)
		(fn [node] (vector (first node) (second node))))
		
		user> (untitle-template)
		"<html><h1></h1></html>"

Bugs and Missing Features
-------------------------

I'm still working on things, particularly more selectors and transformers.

Nonetheless, please let me know of any problems you're having.

License
-------

Eclipse Public License

