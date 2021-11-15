[Setup]
AppName=킣penSPIM
AppVersion=1.0.4
WizardStyle=modern
DefaultDirName={autopf64}\OpenSPIM
DefaultGroupName=OpenSPIM
UninstallDisplayIcon={app}\킣penSPIM.exe
Compression=lzma2
UsePreviousAppDir=no
SolidCompression=yes
OutputBaseFilename=킣penSPIM_setup
OutputDir=userdocs:킣penSPIM Output

[Files]
Source: "target\jfx\native\킣penSPIM\킣penSPIM.exe"; DestDir: "{app}"
Source: "target\jfx\native\킣penSPIM\*.dll"; DestDir: "{app}"
Source: "target\jfx\native\킣penSPIM\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs
Source: "target\jfx\native\킣penSPIM\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{group}\킣penSPIM"; Filename: "{app}\킣penSPIM.exe"