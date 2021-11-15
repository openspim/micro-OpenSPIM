[Setup]
AppName=�OpenSPIM
AppVersion=1.0.4
WizardStyle=modern
DefaultDirName={autopf64}\OpenSPIM
DefaultGroupName=OpenSPIM
UninstallDisplayIcon={app}\�OpenSPIM.exe
Compression=lzma2
UsePreviousAppDir=no
SolidCompression=yes
OutputBaseFilename=�OpenSPIM_setup
OutputDir=userdocs:�OpenSPIM Output

[Files]
Source: "target\jfx\native\�OpenSPIM\�OpenSPIM.exe"; DestDir: "{app}"
Source: "target\jfx\native\�OpenSPIM\*.dll"; DestDir: "{app}"
Source: "target\jfx\native\�OpenSPIM\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs
Source: "target\jfx\native\�OpenSPIM\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{group}\�OpenSPIM"; Filename: "{app}\�OpenSPIM.exe"