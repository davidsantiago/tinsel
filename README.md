Hew
===
Soy and StringTemplate-styled HTML templates using Hiccup.

Introduction
------------

This is a really quick project I threw together as an experiment. I've been 
happily  Hiccup to generate HTML output from Clojure for a while now, and I
love it. The syntax is much nicer to write than HTML, and it compiles to fast
pre-processed code at macro expansion time, boiling down to a bunch of string
concatenations of HTML chunks with your code inserted.

Lately I've been finding my code feeling a bit like spaghetti, though. I open
files and find that I have to give them a good read before I can figure out
what is supposed to be generating HTML chunks and what is logic. I told myself
when I was starting out that I would separate out my templates into functions
that just generated HTML based on their arguments and functions that did
real work. In practice, it turns out that it's all too easy to want to slip
a little bit of code here and there, and lump the related code and templates
close together. Throw in utility functions to massage and organize the
arguments to other functions, and it starts to tangle together.

Hew is my attempt to force myself to be a little bit cleaner about how I
organize my code and data. Instead of code calling into HTML templates which
can call back into code and so on, I'd like to try to force myself to have 
code controlling all the logic, then calling into templates and that's that.

I'm pretty happy with my first cut. My templates look like HTML templates, not
code with HTML in it, and it's easy to use. We'll see how it goes.

Writing Templates
-----------------

Hew is pretty much just Hiccup with a few extra pieces of machinery. Like
Hiccup, Hew does as much work as possible at macro-expansion time, so it tries
to be as efficient as possible at runtime. However, there is probably a little
overhead for making the maps, and it's possible that you will have to add
items to argument maps that might not get used in the template.

The main new construct is the `deftemplate` macro. It takes a name for the 
macro and then Hiccup HTML forms. For example: 

	(ns hew-test-drive
		(:use hew.core))
	
	(deftemplate simple-snippet
		[:h1 "Just a Simple Snippet"])

This generates a function called simple-snippet, which can be called with zero
or one argument. The argument is a map of keys and their corresponding values.
If you wish to insert the contents of a map key into a template, just call
`(attach :keyname)` in the template. 

	(deftemplate simple-snippet
		[:h1 "Welcome to " (attach :site-name)])
		
	(simple-snippet {:site-name "Some Cool Site"})

Which outputs `<h1>Welcome to Some Cool Site</h1>`.

If a key isn't found in the supplied map, nothing gets output in that slot. So
if the no-arguments version is called, any attempts in the template to fetch
items from the map will silently fail. Obviously, if you are calling a
template this way, you probably intended this, or know that the template has
no arguments.

There is a shortcut for getting an item out of the argument map: you can just
deref a symbol using the @ character, and it will fetch the corresponding item
from the argument map, *assuming that item key is a keyword*. You can normally
use any map key you'd like, but with the @ character it must be a keyword.

	(deftemplate equivalent-snippet
		[:h1 "Welcome to " @site-name])
		
	(simple-snippet {:site-name "Some Cool Site"})

You can also compose templates by having one template insert the results of
another template in its midst. You do this with the `call` function. This can
take either no arguments, in which case the called template receives the same
argument map as the caller, or a single map argument, which has its contents
merged into the caller's argument map.

	(deftemplate called-snippet
		[:li @item])
	(deftemplate calling-snippet
		[:ul (for [i @items]
				(call called-snippet {:item i}))])
							
	(calling-snippet {:items [1 2 3]})
	
Which generates `<ul><li>1</li><li>2</li><li>3</li></ul>`.

Oh yeah, and while we're on the subject, you can loop through items or make
conditional decisions based on the contents of the arg map with the familiar
Clojure constructs like `if`,`for`,`cond`, and so on. Like I said, Hew is
really just Hiccup, and Hiccup is just Clojure, so Clojure is available to for
now.

I have a sense that in the future, I will restrict the amount of Clojure
you can use in templates. But for now, I'm not sure how much is enough, so I
am just going to see how well this works without any restrictions. Though, due
to the translation process, some Clojure constructs won't work in templates.
Sympathy is limited, though, because you're not supposed to be doing app logic
in your templates, just layout logic. Obviously, things requiring a deref 
fall into this category, but the workaround is to just not do that sort of
thing in the template!

Bugs and Missing Features
-------------------------

Please let me know of any problems you're having.

License
-------

Eclipse Public License

