# -*- coding: utf-8 -*-
"""一键构建入口（薄封装，引擎在工作区通用工具 common/builder.py）。
用法：本仓库只含配置与源码；构建引擎来自共享构件（common/builder.py），
位置可用环境变量 MB_TOOLS 指定，默认取仓库同级 ../common。"""
import os, sys
PROJ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MB = os.environ.get("MB_TOOLS")
if not MB:
    cand = os.path.normpath(os.path.join(PROJ, os.pardir, "common"))
    if os.path.isdir(cand):
        MB = cand
if not MB or not os.path.isdir(MB):
    raise SystemExit("cannot locate shared module-builder: set MB_TOOLS to the dir containing builder.py")
sys.path.insert(0, MB)
import builder  # noqa: E402
builder.build_project(PROJ)
