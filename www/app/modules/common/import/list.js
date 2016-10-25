
angular.module('os.common.import.list', ['os.common.import.importjob'])
  .controller('ImportJobsListCtrl', function($scope, $translate, importDetail,
    ImportJob, Util, Alerts) {

    function init() {
      $scope.importJobs = [];
      $scope.importDetail = importDetail;
      $scope.pagingOpts = {
        totalJobs: 0,
        currPage: 1,
        jobsPerPage: 25
      };


      $scope.$watch('pagingOpts.currPage', function() {
        loadJobs($scope.pagingOpts);
      });
    };

    function loadJobs(pagingOpts) {
      var startAt = (pagingOpts.currPage - 1) * pagingOpts.jobsPerPage;
      var maxResults = pagingOpts.jobsPerPage + 1;

      var queryParams = {objectType: importDetail.objectTypes, startAt: startAt, maxResults: maxResults};
      ImportJob.query(angular.extend(queryParams, importDetail.objectParams)).then(
        function(importJobs) {
          pagingOpts.totalJobs = (pagingOpts.currPage - 1) * pagingOpts.jobsPerPage + importJobs.length;
 
          if (importJobs.length >= maxResults) {
            importJobs.splice(importJobs.length - 1, 1);
          }

          $scope.importJobs = importJobs;
          angular.forEach(importJobs, function(job) {
            job.outputFileUrl = ImportJob.url() + job.$id() + '/output';
          });
        }
      );
    };

    $scope.stopJob = function(importJob) {
      var inputParams = {
        importJob: importJob,
        importType: 'bulk_imports.import_types.' + importJob.type,
        objectType: importJob.name != 'extensions' ? 'bulk_imports.object_types.' + importJob.name :
          'bulk_imports.extension_name'
      }

      Util.showConfirm({
        ok: function () {
          importJob.stop().then(
            function(resp) {
              loadJobs($scope.pagingOpts);
              Alerts.success('bulk_imports.job_stopped', inputParams);
            }
          )
        },

        title: "bulk_imports.confirm_stop_job",
        isWarning: true,
        confirmMsg: "bulk_imports.confirm_stop_job_title",
        input: inputParams
      });
    }

    init();
  });
