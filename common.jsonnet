{
  local labsjdk8 = {name: 'oraclejdk', version: '8u231-jvmci-19.3-b04', platformspecific: true},

  local labsjdk_ce_11 = {name : 'labsjdk', version : 'ce-11.0.5+10-jvmci-19.3-b04', platformspecific: true},

  jdk8: {
    downloads+: {
      JAVA_HOME: labsjdk8,
      JDT: {name: 'ecj', version: '4.5.1', platformspecific: false},
    },
  },

  jdk11: {
    downloads+: {
      EXTRA_JAVA_HOMES: labsjdk8,
      JAVA_HOME: labsjdk_ce_11,
    },
  },

  deploy:      {targets+: ['deploy']},
  gate:        {targets+: ['gate']},
  postMerge:   {targets+: ['post-merge']},
  bench:       {targets+: ['bench', 'post-merge']},
  dailyBench:  {targets+: ['bench', 'daily']},
  weeklyBench: {targets+: ['bench', 'weekly']},
  weekly:      {targets+: ['weekly']},

  local python3 = {
    environment+: {
      MX_PYTHON_VERSION: "3",
    },
  },

  local common = python3 + {
    packages+: {
      'pip:pylint': '==1.9.3',
      'pip:ninja_syntax': '==1.7.2',
    },
    catch_files+: [
      'Graal diagnostic output saved in (?P<filename>.+.zip)',
      'npm-debug.log', // created on npm errors
    ],
    environment+: {
      GRAALVM_CHECK_EXPERIMENTAL_OPTIONS: "true",
    },
  },

  linux: common + {
    packages+: {
      'apache/ab': '==2.3',
      binutils: '==2.23.2',
      gcc: '==8.3.0',
      git: '>=1.8.3',
      maven: '==3.3.9',
      valgrind: '>=3.9.0',
    },
    capabilities+: ['linux', 'amd64'],
  },

  ol65: self.linux + {
    capabilities+: ['ol65'],
  },

  x52: self.linux + {
    capabilities+: ['no_frequency_scaling', 'tmpfs25g', 'x52'],
  },

  sparc: common + {
    capabilities: ['solaris', 'sparcv9'],
  },

  linux_aarch64: common + {
    capabilities+: ['linux', 'aarch64'],
    packages+: {
      gcc: '==8.3.0',
    }
  },

  darwin: common + {
    environment+: {
      // for compatibility with macOS El Capitan
      MACOSX_DEPLOYMENT_TARGET: '10.11',
    },
    capabilities: ['darwin', 'amd64'],
  },
}
