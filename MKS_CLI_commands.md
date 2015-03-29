# Viewsandbox commands #

## all members locally modified ##
si viewsandbox --filter=changed:working


## all out of sync members ##
> si viewsandbox --filter=changed:sync
> => candidates for incoming changes

## modified without checkout members ##
si viewsandbox --filter=changed:working --filter=!locked:$user$
> (locally changed, but not locked by user)

# Add member command #
si add --createSubprojects --cpid=xx --defer --description="some text" "member path"

# How to ... #
## revert a deferred add operation ##
> si revert "filename"
this does not delete the file itself

## fetch new members of a sandbox ##
> si resync --filter=changed:newmem
will only syncrhonize new members

## resynchronize only non locally-modified files ##
si resync --filter=changed:sync --filter=!changed:working
## resynchronize only newly added files ##
si resync --filter=changed:newmem --filter=!changed:working


# Important : encoding issues #
It seems encodings need to be set differently depending on the commands.

On my Installation (French machine, French server), it seems most of si commands (especially si viewcps, and any command that outputs dates) need to be run with IBM850 encoding.

But the encoding used to checkin files seems different : ISO-8859-1 needs to be used for si viewrevision. This encoding is indeed the one that was used to create the file initially

# how --filter:file works #
it filters all files whose relative path to the bottommost sandbox matches the provided string.

examples
> si viewsandbox --recurse --filter=file:**sr**
> all files whose path relative to the current dir contain the sr string. Will not return files members of other subsandboxes if any
> si viewsandbox --recurse --filter=file:src/**> all files belonging to the src subdir
> si viewsandbox --recurse --filter=file:src/**.java
> all java files belonging to the src subdir
all of these would select all java files belong to