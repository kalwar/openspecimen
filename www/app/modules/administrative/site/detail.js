angular.module('os.administrative.site.detail', ['os.administrative.models'])
  .controller('SiteDetailCtrl', function($scope, $q, site, Institute, PvManager) {

    function init() {
      $scope.site = site;
      $scope.institutes = [];
      loadPvs();
    }

    function loadPvs() {
      $scope.siteTypes = PvManager.getPvs('site-type');

      Institute.query().then(
        function(instituteList) {
          angular.forEach(instituteList, function(institute) {
            $scope.institutes.push(institute.name);
          });
        }
      );
    }

    $scope.editSite = function(property, value) {
      var d = $q.defer();
      d.resolve({});
      return d.promise;
    }

    $scope.getCoordinatorDisplayText = function(coordinator) {
      return coordinator.lastName + ' ' + coordinator.firstName;
    }

    init();
  });