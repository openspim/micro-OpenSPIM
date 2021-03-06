[Setup]
AppName=ľOpenSPIM
AppVersion=1.0.4
WizardStyle=modern
DefaultDirName={autopf64}\OpenSPIM
DefaultGroupName=OpenSPIM
UninstallDisplayIcon={app}\ľOpenSPIM.exe
Compression=lzma2
UsePreviousAppDir=no
SolidCompression=yes
OutputBaseFilename=ľOpenSPIM_setup
OutputDir=userdocs:ľOpenSPIM Output

[Files]
Source: "target\jfx\native\ľOpenSPIM\ľOpenSPIM.exe"; DestDir: "{app}"
Source: "target\jfx\native\ľOpenSPIM\*.dll"; DestDir: "{app}"
Source: "target\jfx\native\ľOpenSPIM\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs
Source: "target\jfx\native\ľOpenSPIM\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{group}\ľOpenSPIM"; Filename: "{app}\ľOpenSPIM.exe"