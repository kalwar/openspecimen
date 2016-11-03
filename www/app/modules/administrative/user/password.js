angular.module('os.administrative.user.password', ['os.administrative.models'])
  .controller('UserPasswordCtrl', function(
    $scope, $rootScope, $state, $stateParams, user,
    User, SettingUtil) {
 
    function init() {
      $scope.user = user;
      $scope.passwordDetail = {userId: user.id};
      $scope.passwdCtx = {};
      loadPasswdRules();
    }

    $scope.updatePassword = function() {
      User.changePassword($scope.passwordDetail).then(
      function(result) {
        if (result) {
          $state.go('user-detail.overview', {userId: $scope.user.id});
        }
      });
    }

    function loadPasswdRules() {
      SettingUtil.getSetting("auth", "password_pattern").then(
        function(setting) {
          $scope.passwdCtx.pattern = setting.value;
        }
      );

      SettingUtil.getSetting("auth", "password_rule").then(
        function(setting) {
          $scope.passwdCtx.rule = setting.value;
        }
      );
    }

    init();
  });
