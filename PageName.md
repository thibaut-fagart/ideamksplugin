# Introduction #
This page presents the various ways to interact with MKS

# mkscmapi.jar #
Contains a set of classes (bound using a native library) allowing limited interaction with the mks local client.

Available operations are
  * checkout members
  * add members
  * drop members
  * resync members
  * revert members
  * view member differences
  * view member history
  * view member information
  * view sandbox

All of these actions involve intrusive native-client-owned dialogs.

# CLI interface #
MKS installation provides a command line interface with more possibilities.
si.exe allows the following operations
```
 about                   About
 acceptcp                Accept Change Package
 add                     Add Members
 addlabel                Add Label
 addmemberattr           Add Member Attribute
 addmemberfromarchive    Add Members From Archive
 addproject              Add Projects
 addprojectattr          Add Project Attribute
 addprojectlabel         Add Project Label
 addsandboxattr          Add Sandbox Attribute
 addsubproject           Add Subproject
 annotate                Annotated Revision View
 appendrevdesc           Append Revision Description
 applycp                 Apply Change Package
 archiveinfo             Archive Information View
 checkpoint              Checkpoint
 ci                      Check In
 closecp                 Close Change Package
 co                      Check Out
 configuresandbox        Configure Sandbox
 configuresubproject     Configure Subproject
 connect                 Connect To Server
 cpissues                View Issues which can have Open Change Packages
 createcp                Create Change Package
 createdevpath           Create Development Path
 createproject           Create Projects
 createsandbox           Create Sandboxes
 createsubproject        Create Subprojects
 deletelabel             Delete Label
 deleteprojectlabel      Delete Project Label
 deleterevision          Delete Revision
 demote                  Demote
 demoteproject           Demote Project
 diff                    Diff
 discardcp               Discard Change Package
 disconnect              Disconnect From Server
 drop                    Drop
 dropdevpath             Drop Development Path
 dropmemberattr          Drop Member Attribute
 dropproject             Drop Projects
 dropprojectattr         Drop Project Attribute
 dropsandbox             Drop Sandboxes
 dropsandboxattr         Drop Sandbox Attribute
 echo                    Echo String in UI Appropriate manner
 edit                    Edit
 editcp                  Edit Change Package
 exit                    Exit
 freeze                  Freeze
 gui                     Launch the Graphical Interface
 import                  Import Members
 importproject           Import Projects
 importsandbox           Import Sandboxes
 integrations            Enable/Disable Integrations
 loadrc                  Load Configuration
 lock                    Lock
 locks                   Locks View
 makewritable            Make Writable
 memberinfo              Member Information View
 merge                   Merge
 mergebranch             Merge Branch
 mods                    Project Differences View
 opencp                  Open Change Package
 print                   Archive Print View
 projectinfo             Project Information View
 projects                Registered Projects View
 promote                 Promote
 promoteproject          Promote Project
 rejectcp                Reject Change Package
 rename                  Rename Member
 report                  Project Report
 restoreproject          Restore Project
 resync                  Resynchronize
 resynccp                Resynchronize Change Package
 revert                  Revert
 revisioninfo            Revision Information View
 rlog                    Archive Report View
 sandboxes               Registered Sandboxes View
 sandboxinfo             Sandbox Information View
 servers                 Server Connections View
 setprefs                Configure Preferences
 setprojectdescription   Set Project Description
 sharesubproject         Add Shared Subproject
 snapshot                Snapshot Sandbox
 submit                  Submit
 submitcp                Submit Change Package
 thaw                    Thaw
 unlock                  Unlock
 unlockarchive           Unlock Archive
 updatearchive           Update Archive Attributes
 updateclient            Update Client
 updaterevision          Update Revision
 viewcp                  Change Package View
 viewcps                 Change Packages View
 viewhistory             Member History View
 viewlabels              Member Labels View
 viewlocks               Report on Archive Locks
 viewnonmembers          Non-Members View
 viewprefs               Preferences View
 viewproject             Project View
 viewprojecthistory      Project History View
 viewrevision            Revision Contents View
 viewsandbox             Sandbox View
```


Add your content here.  Format your content with:
  * Text in **bold** or _italic_
  * Headings, paragraphs, and lists
  * Automatic links to other wiki pages


