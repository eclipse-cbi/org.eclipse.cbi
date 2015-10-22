module.exports = function(grunt) {
  var i, len, plugin, ref;
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    less: {
      dev: {
        src: ['./_src/less/styles.less'],
        dest: './static/public/css/solstice.css'
      },
      dist: {
        options: {
          compress: true,
          // minifying the result
        },
        src: ['./_src/less/styles.less'],
        dest: './static/public/css/solstice.css'
      }
    },
    uglify: {
      dist: {
          options: {
              mangle: false
                  // Use if you want the names of your functions and variables
                  // unchanged.
          },
        src: './static/public/js/solstice.js',
        dest: './static/public/js/solstice.js'
      }
    },
    copy: {
      main: {
        files: [
          // includes files within path
          {
            expand: true,
            flatten: true,
            src: ['./bower_components/bootstrap/fonts/*'],
            dest: './static/public/fonts/',
            filter: 'isFile'
          }, {
            expand: true,
            flatten: true,
            src: ['./bower_components/fontawesome/fonts/*'],
            dest: './static/public/fonts/',
            filter: 'isFile'
          }, {
            expand: true,
            cwd: './bower_components/solstice-assets/images/',
            src: ['**'],
            dest: './static/public/images/',
            filter: 'isFile'
          }
        ]
      }
    },
    concat: {
      options: {
        separator: ';',
      },
      js: {
        src: ['./bower_components/jquery/dist/jquery.js', 
              './bower_components/bootstrap/dist/js/bootstrap.js', 
              './_src/js/main.js'],
        dest: './static/public/js/solstice.js',
      },
   },
    watch: {
      options: {
        atBegin: true,
        livereload: true
      },
      less: {
        files: ['./_src/less/*.less'],
        tasks: 'less:dev'
      },
      js: {
          files: ['./_src/js/*.js'],
          tasks: 'concat'
        },
      all: {
        files: ['Gruntfile.js'],
        tasks: 'dev'
      }
    }
  });

  ref = ['grunt-contrib-uglify', 
         'grunt-contrib-copy', 
         'grunt-contrib-less',
         'grunt-contrib-concat',
         'grunt-contrib-watch'];
  for (i = 0, len = ref.length; i < len; i++) {
    plugin = ref[i];
    grunt.loadNpmTasks(plugin);
  }
  
  grunt.registerTask('dev', ['copy', 'less:dev', 'concat', 'uglify']);
  grunt.registerTask('default', ['copy', 'less:dist', 'concat', 'uglify']);
};