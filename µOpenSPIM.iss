[Setup]
AppName=µOpenSPIM
AppVersion=1.0
WizardStyle=modern
DefaultDirName={autopf}\OpenSPIM
DefaultGroupName=OpenSPIM
UninstallDisplayIcon={app}\µOpenSPIM.exe
Compression=lzma2
SolidCompression=yes
OutputDir=userdocs:µOpenSPIM Output

[Files]
Source: "target\jfx\native\µOpenSPIM\µOpenSPIM.exe"; DestDir: "{app}"
Source: "target\jfx\native\µOpenSPIM\*.dll"; DestDir: "{app}"
Source: "target\jfx\native\µOpenSPIM\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs
Source: "target\jfx\native\µOpenSPIM\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{group}\µOpenSPIM"; Filename: "{app}\µOpenSPIM.exe"